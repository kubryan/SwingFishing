package com.swingfishing.fishing.game;

import com.swingfishing.SwingFishing;
import com.swingfishing.config.ModConfig;
import com.swingfishing.fishing.FishRarity;
import com.swingfishing.fishing.FishRarityLookup;
import com.swingfishing.fishing.data.FishingMiniGameData;
import com.swingfishing.fishing.data.ModAttachments;
import com.swingfishing.network.MiniGameSyncPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 釣魚小遊戲的伺服器端狀態機（NeoForge 1.21.1）。
 *
 * <p><b>權威性：</b>所有狀態轉換、計時、成功/失誤計數、逾時判定、
 * 戰利品發放與防呆清理皆由此類別在伺服器端執行。</p>
 *
 * <p><b>Config 讀取：</b>每次判定皆即時讀取 {@link ModConfig} 的最新值，
 * 不進行快取。</p>
 *
 * <p><b>Client HUD 通知：</b>透過 {@link ClientNotifier} 介面解耦。
 * 若 {@code com.swingfishing.network} 尚未建立，可先注入一個 no-op 實作；
 * 網路層完成後，注入發送封包的實作即可。</p>
 */
public final class FishingMiniGameManager {

    // ============================================================
    // Client 通知介面（網路層預留銜接點）
    // ============================================================

    /**
     * 客戶端 HUD 通知器。
     * <p>伺服器端狀態機透過此介面發送「開始、更新、成功、失敗、結束」等事件，
     * 不直接依賴任何網路封包類別。網路層完成後，提供一個實作發送
     * {@code CustomPacketPayload} 的實例即可。</p>
     */
    public interface ClientNotifier {

        /** 通知客戶端開始小遊戲，並附上第一個目標方向。 */
        void onGameStarted(ServerPlayer player, SwipeDirection direction);

        /** 通知客戶端目標方向已更新（成功跟上後）。 */
        void onDirectionChanged(ServerPlayer player, SwipeDirection nextDirection);

        /** 通知客戶端成功一次，附上當前成功次數。 */
        void onSuccess(ServerPlayer player, int successCount, int requiredSuccess);

        /** 通知客戶端發生一次失誤或逾時，附上當前失誤次數。 */
        void onFailure(ServerPlayer player, int failedCount, int maxFailed);

        /** 通知客戶端小遊戲已結束（成功釣起或脫鉤），應隱藏 HUD。 */
        /** 通知客戶端小遊戲已結束，並附上結束原因（CAUGHT / ESCAPED / ABORTED）。 */
        void onGameEnded(ServerPlayer player, MiniGameSyncPayload.EndReason reason);
    }

    private static ClientNotifier notifier = new ClientNotifier() {
        // 預設 no-op：網路層尚未建立時的安全實作。
        @Override public void onGameStarted(ServerPlayer player, SwipeDirection direction) {}
        @Override public void onDirectionChanged(ServerPlayer player, SwipeDirection nextDirection) {}
        @Override public void onSuccess(ServerPlayer player, int successCount, int requiredSuccess) {}
        @Override public void onFailure(ServerPlayer player, int failedCount, int maxFailed) {}
        @Override public void onGameEnded(ServerPlayer player, MiniGameSyncPayload.EndReason reason) {}
    };

    /**
     * 注入 Client 通知器。應在網路層初始化完成後呼叫。
     * @param impl 實際的封包發送實作
     */
    public static void setClientNotifier(ClientNotifier impl) {
        if (impl != null) {
            notifier = impl;
        }
    }

    private FishingMiniGameManager() {
        // 工具類別，禁止實例化。
    }

    // ============================================================
    // 生命週期：開始
    // ============================================================

    /**
     * 開始一輪新的小遊戲。
     * <p>由 {@link FishingGameEvents} 在 {@code ItemFishedEvent} 中呼叫。
     * 呼叫端應先確保玩家的 {@link FishingMiniGameData} 處於非活躍狀態。</p>
     *
     * @param player  伺服器端玩家
     * @param hook    觸發此事件的浮標實體
     */
    public static void startGame(ServerPlayer player, FishingHook hook) {
        if (!ModConfig.ENABLE_HEAD_SWING_FISHING.get()) {
            return;
        }
        if (player.level().isClientSide()) {
            return;
        }

        FishingMiniGameData data = player.getData(ModAttachments.FISHING_MINI_GAME.get());
        if (data.isActive()) {
            return;
        }

        SwipeDirection firstDirection = randomDirection();
        data.start(firstDirection, hook.getUUID(), hook.blockPosition(),
                player.level().dimension().location(), 0);

        Set<String> biomePool = resolveBiomeFishPool(player, data);
        FishRarity rarity = FishRarity.roll();
        String fishId = pickFish(rarity, biomePool);

        if (fishId == null) {
            // 抽不到魚：必須清掉 active，否則會卡死 / 下一 tick 被 abort
            data.clear();
            return;
        }

        // 難度以「實際抽到的魚」的稀有度為準
        FishRarity actualRarity = FishRarityLookup.get(fishId);
        data.setRolledCatch(actualRarity, fishId);

        notifier.onGameStarted(player, firstDirection);
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /**
     * 從 preferred 稀有度開始，在生態域池內抽一條魚；該階沒魚則往下找，再往上找。
     */
    private static String pickFish(FishRarity preferred, Set<String> biomePool) {
        FishRarity[] order = FishRarity.values();
        int start = preferred.ordinal();

        for (int i = start; i >= 0; i--) {
            Set<String> candidates = FishRarityLookup.getFishOfRarityInPool(order[i], biomePool);
            if (!candidates.isEmpty()) {
                String[] arr = candidates.toArray(new String[0]);
                return arr[(int) (Math.random() * arr.length)];
            }
        }
        for (int i = start + 1; i < order.length; i++) {
            Set<String> candidates = FishRarityLookup.getFishOfRarityInPool(order[i], biomePool);
            if (!candidates.isEmpty()) {
                String[] arr = candidates.toArray(new String[0]);
                return arr[(int) (Math.random() * arr.length)];
            }
        }
        return null;
    }





    /**
     * 僅供 Debug 測試使用：強制玩家開始小遊戲，並將玩家 UUID 作為假浮標。
     */
    public static void debugStartGame(ServerPlayer player) {
        FishingMiniGameData data = player.getData(ModAttachments.FISHING_MINI_GAME.get());
        data.start(SwipeDirection.UP, player.getUUID(), 0);
        notifier.onGameStarted(player, SwipeDirection.UP);
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("[Debug] 小遊戲已啟動！目標：UP"));
    }

    // ============================================================
    // 每 tick 推進
    // ============================================================

    /**
     * 每 tick 由 {@link FishingGameEvents} 呼叫。
     * <p>執行計時推進、逾時判定、以及所有防呆中斷檢查。</p>
     *
     * @param player 伺服器端玩家
     */
    public static void tickPlayer(ServerPlayer player) {
        FishingMiniGameData data = player.getData(ModAttachments.FISHING_MINI_GAME.get());
        if (!data.isActive()) {
            return;
        }

        // 防呆：Config 被關閉
        if (!ModConfig.ENABLE_HEAD_SWING_FISHING.get()) {
            abortGame(player, data, AbortReason.CONFIG_DISABLED);
            return;
        }

        // Debug 指令使用玩家 UUID 作為假浮標，因此不要求手持釣竿。
        boolean isDebug = data.getBobberUUID() != null && data.getBobberUUID().equals(player.getUUID());

        // ⚠️ 不要在小遊戲進行中因「浮標消失」而中斷。
        // 原因：ItemFishedEvent 取消戰利品後，原版仍可能收回/discard 浮標，
        // 若此處檢查 isBobberAlive，會在 startGame 後 1 tick 就 BOBBER_LOST。
        // 浮標方塊座標已在 start 時寫入 data，結算選表不需要活著的實體。

        // 防呆：玩家已不在釣竿狀態（真正換手/收起才中斷）
        if (!isDebug
                && !(player.getMainHandItem().getItem() instanceof net.minecraft.world.item.FishingRodItem)
                && !(player.getOffhandItem().getItem() instanceof net.minecraft.world.item.FishingRodItem)) {
            abortGame(player, data, AbortReason.ROD_CHANGED);
            return;
        }

        // 計時推進
        data.tickTimer();

        // 超時判定（含魚餌附魔加成）
        if (data.getTicksElapsed() >= computeTimeoutTicks(player)) {
            handleTimeout(player, data);
        }
    }

    /**
     * 計算當前玩家的作答超時 ticks。
     * <p>優先使用本回合預抽稀有度的 timeoutTicks；若無稀有度（舊流程／debug）則回退 Config。
     * 再疊加魚餌（Lure）附魔加成。</p>
     */
    public static int computeTimeoutTicks(ServerPlayer player) {
        FishingMiniGameData data = player.getData(ModAttachments.FISHING_MINI_GAME.get());
        int base = data.getRolledRarity() != null
                ? data.getRolledRarity().timeoutTicks
                : ModConfig.SWIPE_TIMEOUT_TICKS.get();
        int lureLevel = getFishingRodEnchantmentLevel(
                player, net.minecraft.world.item.enchantment.Enchantments.LURE);
        int perLevel = ModConfig.LURE_BONUS_TICKS_PER_LEVEL.get();
        int bonus = Math.max(0, lureLevel) * Math.max(0, perLevel);
        return base + bonus;
    }

    /**
     * 檢查浮標是否仍存活，用於 {@link #tickPlayer} 的防呆判定。
     *
     * <p>回傳 {@code false} 代表小遊戲應立即中斷，涵蓋以下情況（任一成立）：</p>
     * <ul>
     *   <li><b>維度不符</b>：玩家已不在浮標所在維度（例如非傳送門的強制 {@code /tp}、
     *       跨維度插件傳送等）。</li>
     *   <li><b>浮標實體不存在</b>且所在區塊已載入：被岩漿燒掉、被 {@code /kill} 清除、
     *       水流推離水面後原版 {@code discard}、玩家跑太遠原版自動收回等。</li>
     * </ul>
     *
     * <p><b>區塊未載入時視為存活</b>：避免玩家遠離浮標或伺服器存檔載入初期，
     * 因 {@code getEntity} 暫時回傳 null 而誤觸 abort。</p>
     *
     * <p><b>舊存檔相容</b>：若 {@code bobberBlockPos} 為 null（S1 前的存檔），
     * 無法判斷區塊是否載入，只能直接看 {@code getEntity} 結果。</p>
     */
    private static boolean isBobberAlive(ServerPlayer player, FishingMiniGameData data) {
        UUID bobberUUID = data.getBobberUUID();
        if (bobberUUID == null) {
            return false;
        }

        // 維度不符：浮標一定找不回
        ResourceLocation bobberDim = data.getBobberDimension();
        if (bobberDim != null
                && !bobberDim.equals(player.level().dimension().location())) {
            return false;
        }

        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }

        // 區塊未載入 → 暫時無法判斷，視為存活（等待載入）
        BlockPos bobberPos = data.getBobberBlockPos();
        if (bobberPos != null && !level.isLoaded(bobberPos)) {
            return true;
        }

        FishingHook hook = findHook(player, bobberUUID);
        return hook != null && hook.isAlive();
    }

    // ============================================================
    // 甩動結果判定
    // ============================================================

    /**
     * 處理來自網路層的甩動輸入。
     * <p>呼叫端（封包處理器）必須已在主執行緒中執行（透過 {@code context.enqueueWork}），
     * 並確認玩家與 {@link FishingMiniGameData} 皆有效。</p>
     *
     * <p><b>超時計時器行為：</b>只有「成功」與「失誤」會重置 {@code ticksElapsed}；
     * 「幅度不足」與「模糊地帶」<b>不重置</b>，避免玩家持續微小抖動就能無限拖延。
     * 若玩家長時間卡在模糊地帶，超時仍會自然觸發。</p>
     *
     * @param player     伺服器端玩家
     * @param deltaYaw   Yaw 增量（degrees）
     * @param deltaPitch Pitch 增量（degrees）
     */
    public static void handleSwipe(ServerPlayer player, double deltaYaw, double deltaPitch) {
        FishingMiniGameData data = player.getData(ModAttachments.FISHING_MINI_GAME.get());
        if (!data.isActive() || data.getTargetDirection() == null) {
            return;
        }

        // 處於歸位冷卻中，忽略輸入
        if (data.isIgnoringInput()) {
            return;
        }

        // 累加本次輸入
        data.accumulateSwipe(deltaYaw, deltaPitch);
        double accumYaw = data.getAccumulatedYaw();
        double accumPitch = data.getAccumulatedPitch();

        // 未達最小門檻：累積保留（讓慢速甩動能持續累加），但超時計時器不重置
        if (Math.hypot(accumYaw, accumPitch) < ModConfig.MIN_ANGLE_DEGREES.get()) {
            return;
        }

        SwipeDirection target = data.getTargetDirection();

        // 方向正確且幅度足夠
        if (target.matches(accumYaw, accumPitch)) {
            data.resetAccumulation();
            handleSuccess(player, data);
            return;
        }

        // 方向明顯反向（超過 90° 偏差）→ 失誤
        if (target.isClearlyWrong(accumYaw, accumPitch)) {
            data.resetAccumulation();
            handleFailure(player, data);
            return;
        }

        // 模糊地帶（偏差 22.5°~90°）：不計成功也不計失誤，只清累積
        // 不重置 ticksElapsed，讓超時計時器持續推進；方向未變，不需同步客戶端
        data.resetAccumulation();
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.PLAYERS, 0.2F, 1.8F);
    }

    // ============================================================
    // 成功 / 失敗 / 逾時
    // ============================================================

    private static void handleSuccess(ServerPlayer player, FishingMiniGameData data) {
        int requiredSuccess = data.getRolledRarity() != null
                ? data.getRolledRarity().requiredSuccess
                : ModConfig.REQUIRED_SUCCESS_SWIPES.get();

        data.setIgnoreTicks(ModConfig.SWIPE_COOLDOWN_TICKS.get());

        FishingHook hook = findHook(player, data.getBobberUUID());
        double px = hook != null ? hook.getX() : player.getX();
        double py = hook != null ? hook.getY() + 0.2 : player.getY() + 0.5;
        double pz = hook != null ? hook.getZ() : player.getZ();

        if (data.getSuccessCount() + 1 >= requiredSuccess) {
            data.incrementSuccess(null);
            finishGame(player, data, true, px, py, pz);
            return;
        }

        SwipeDirection next = randomDirection();
        data.incrementSuccess(next);

        notifier.onSuccess(player, data.getSuccessCount(), requiredSuccess);
        notifier.onDirectionChanged(player, next);

        player.level().playSound(null, px, py, pz,
                SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.PLAYERS, 0.8F, 1.2F);

        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.SPLASH,
                    px, py, pz, 8, 0.2, 0.2, 0.2, 0.05);
        }
    }

    private static void handleFailure(ServerPlayer player, FishingMiniGameData data) {
        int maxFailed = data.getRolledRarity() != null
                ? data.getRolledRarity().maxFailed
                : ModConfig.MAX_FAILED_SWIPES.get();

        data.incrementFailed();
        data.setIgnoreTicks(ModConfig.SWIPE_COOLDOWN_TICKS.get());

        FishingHook hook = findHook(player, data.getBobberUUID());
        double px = hook != null ? hook.getX() : player.getX();
        double py = hook != null ? hook.getY() + 0.2 : player.getY() + 0.5;
        double pz = hook != null ? hook.getZ() : player.getZ();

        notifier.onFailure(player, data.getFailedCount(), maxFailed);
        player.level().playSound(null, px, py, pz,
                SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.PLAYERS, 0.6F, 0.5F);

        if (data.getFailedCount() >= maxFailed) {
            finishGame(player, data, false, px, py, pz);
        }
    }

    /** 依浮標位置決定本生態域可用的魚 ID 池（不含命名空間）。 */
    /** 依浮標位置決定本生態域可用的魚 ID 池（不含命名空間）。 */
    private static Set<String> resolveBiomeFishPool(ServerPlayer player, FishingMiniGameData data) {
        BlockPos bobberPos = data.getBobberBlockPos();
        ResourceLocation bobberDimension = data.getBobberDimension();
        if (bobberPos == null || bobberDimension == null) {
            bobberPos = player.blockPosition();
            bobberDimension = player.level().dimension().location();
        }

        if (Level.NETHER.location().equals(bobberDimension)) {
            return Set.of(
                    "deepslatefish", "embergill", "fossilized_angelfish", "glowing_dark",
                    "glowstone_pufferfish", "glowstone_seeker", "magma_fish", "scorchfish",
                    "sculk_ward", "sculkfish"
            );
        }
        if (Level.END.location().equals(bobberDimension)) {
            return Set.of(
                    "aurora", "elderscale", "end_glow", "end_jellyfish", "end_starfish",
                    "end_starfish_shashlik", "suneater", "vesani", "voidbiter", "voidfin", "ward"
            );
        }
        if (player.serverLevel().getBiome(bobberPos).is(BiomeTags.IS_OCEAN)) {
            return Set.of(
                    "amethystback", "anglerfish", "bigeye_tuna", "blossomfish", "blue_herring",
                    "bluegigi", "boreal", "bright_amethyst_snapper", "cactifish", "cerberay",
                    "charfish", "cryospine", "crystalback_boreal", "dark_amethyst_snapper",
                    "deepjaw_herring", "divine_jellyfish", "dreamliner", "dusktail_snapper",
                    "fish_bones", "flowerfish", "garnet_mackerel", "gold_fan", "ice_cod",
                    "ironjaw_herring", "jellyfish", "joel", "lily_snapper", "livid_bamboo",
                    "luminous_mackerel", "luminous_shrimp", "mangrove_jack", "mistback_chub",
                    "morganite", "mothfish", "obidontiee", "ordinary_mackerel", "ordinary_shrimp",
                    "pale_pinfish", "pandafish", "peakdweller", "pinfish", "redscaled_tuna",
                    "rose_siamese_fish", "sandtail", "shroomfish", "solarfish", "sporefish",
                    "starfish", "starfish_shashlik", "stonefish", "tentacle", "the_quarrish",
                    "whiteveil", "willish", "witchfish", "yellowstone_fish"
            );
        }
        // river / 一般淡水
        return Set.of(
                "agave_bream", "aquamarine_pike", "azure_crystalback_minnow", "blue_crystal_fin",
                "blue_ice_pike", "carpenjoe", "charged_thunderfin", "chorus_minnow", "cloudfin",
                "crystalback_minnow", "crystalback_sturgeon", "crystalback_trout", "deformed_carp",
                "divine_catfish", "downfall_bream", "driftfin", "drifting_bream", "dripfin", "eel",
                "frostgill_chub", "frostjaw_trout", "ghostly_pike", "glimmergill", "hollowbelly_darter",
                "icetooth_sturgeon", "lightning_bass", "lilac_minnow", "luminous_carp", "lush_pike",
                "mirage_carp", "mossfin", "muddy_catfish", "oasis_sturgeon", "ordinary_carp",
                "ordinary_catfish", "pale_carp", "petal_bass", "petaldrift_carp", "pink_koi",
                "purple_carp", "pyrotrout", "rainfin", "red_koi", "ripple_catfish", "rockgill",
                "sage_catfish", "scalding_pike", "sea_bass", "shadowfin", "silverfin_pike",
                "silverveil_perch", "sludge_catfish", "sun_seeking_carp", "sunflower_carp",
                "sunny_sturgeon", "thunder_bass", "thunderfin", "tropical_catfish",
                "vivid_moss", "willow_bream", "wintery_pike", "yellow_koi"
        );
    }

    private static void handleTimeout(ServerPlayer player, FishingMiniGameData data) {
        int maxFailed = data.getRolledRarity() != null
                ? data.getRolledRarity().maxFailed
                : ModConfig.MAX_FAILED_SWIPES.get();

        data.incrementFailed();
        data.resetAccumulation();
        data.setIgnoreTicks(ModConfig.SWIPE_COOLDOWN_TICKS.get());

        FishingHook hook = findHook(player, data.getBobberUUID());
        double px = hook != null ? hook.getX() : player.getX();
        double py = hook != null ? hook.getY() + 0.2 : player.getY() + 0.5;
        double pz = hook != null ? hook.getZ() : player.getZ();

        notifier.onFailure(player, data.getFailedCount(), maxFailed);
        player.level().playSound(null, px, py, pz,
                SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.PLAYERS, 0.8F, 0.4F);

        if (data.getFailedCount() >= maxFailed) {
            finishGame(player, data, false, px, py, pz);
        }
    }

    // ============================================================
    // 結束：成功釣起 / 脫鉤 / 防呆中斷
    // ============================================================

    /**
     * 成功釣起：發放戰利品、移除浮標、清理狀態。
     */
    private static void finishGame(ServerPlayer player, FishingMiniGameData data, boolean caught, double px, double py, double pz) {
        if (caught) {
            // 發放我們專屬的魚類戰利品
            grantFishingLoot(player, data);
            // 成功收線：播放聲音並清空狀態
            removeHook(player, data.getBobberUUID(), true);

            // 爆發水花
            if (player.level() instanceof ServerLevel serverLevel) {
                serverLevel.playSound(null, px, py, pz,
                        SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7F, 1.5F);
                serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.SPLASH,
                        px, py, pz, 40, 0.5, 0.5, 0.5, 0.2);
            }
        } else {
            // 失敗脫鉤：直接收回，不播成功音效
            removeHook(player, data.getBobberUUID(), false);
        }

        data.clear();
        notifier.onGameEnded(player, caught
                ? MiniGameSyncPayload.EndReason.CAUGHT
                : MiniGameSyncPayload.EndReason.ESCAPED);
    }
    /**
     * 防呆中斷：清理狀態、通知客戶端隱藏 HUD，不發放戰利品。
     */
    public static void abortGame(ServerPlayer player, FishingMiniGameData data, AbortReason reason) {
        if (!data.isActive()) {
            return;
        }
        if (reason != AbortReason.BOBBER_LOST) {
            removeHook(player, data.getBobberUUID(), false);
        }
        data.clear();
        notifier.onGameEnded(player, MiniGameSyncPayload.EndReason.ABORTED);
    }

    /** 中斷原因列舉。 */
    public enum AbortReason {
        CONFIG_DISABLED,
        ROD_CHANGED,
        BOBBER_LOST,
        PLAYER_DEATH,
        DIMENSION_CHANGE,
        LOGOUT
    }

    // ============================================================
    // 戰利品與浮標
    // ============================================================

    /**
     * 發放本回合預抽的魚。
     * <p>上鉤時已決定 {@code rolledFishId}；成功後直接發放，不再重新 roll Loot Table。
     * 若因舊存檔／debug 沒有預抽結果，則回退到原本的生態域 Loot Table。</p>
     */
    private static void grantFishingLoot(ServerPlayer player, FishingMiniGameData data) {
        String fishId = data.getRolledFishId();

        if (fishId != null && !fishId.isEmpty()) {
            ResourceLocation itemKey = ResourceLocation.fromNamespaceAndPath(SwingFishing.MOD_ID, fishId);
            var item = BuiltInRegistries.ITEM.get(itemKey);
            if (item != null && item != net.minecraft.world.item.Items.AIR) {
                ItemStack stack = new ItemStack(item);
                Component fishName = stack.getHoverName(); // ✅ 先存好，count 還是 1

                if (!player.getInventory().add(stack)) {
                    player.drop(stack, false);
                }
                if (data.addCaughtFish(fishId)) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                            "message.swingfishing.fish_book.new_discovery", fishName)); // ✅ 用存好的
                    player.level().playSound(null, player.blockPosition(),
                            SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5F, 2.0F);
                }
                return;
            }
        }

        // Fallback：舊 Loot Table
        ResourceKey<LootTable> lootTableKey = ResourceKey.create(
                Registries.LOOT_TABLE, selectFishingLootTable(player, data));
        LootTable lootTable = player.serverLevel().getServer().reloadableRegistries()
                .getLootTable(lootTableKey);
        if (lootTable == null) {
            return;
        }

        ItemStack rod = player.getMainHandItem().getItem() instanceof net.minecraft.world.item.FishingRodItem
                ? player.getMainHandItem() : player.getOffhandItem();
        LootParams params = new LootParams.Builder(player.serverLevel())
                .withParameter(LootContextParams.ORIGIN, player.position())
                .withParameter(LootContextParams.TOOL, rod)
                .withParameter(LootContextParams.THIS_ENTITY, player)
                .withParameter(LootContextParams.ATTACKING_ENTITY, player)
                .withLuck(player.getLuck())
                .create(LootContextParamSets.FISHING);

        for (ItemStack stack : lootTable.getRandomItems(params)) {
            Item stackItem = stack.getItem();         // ✅ 先存好
            Component stackName = stack.getHoverName(); // ✅ 先存好

            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stackItem); // ✅ 用存好的
            if (SwingFishing.MOD_ID.equals(itemId.getNamespace())
                    && !"fish_book".equals(itemId.getPath())
                    && data.addCaughtFish(itemId.getPath())) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                        "message.swingfishing.fish_book.new_discovery", stackName)); // ✅
            }
        }
    }



    private static ResourceLocation selectFishingLootTable(ServerPlayer player, FishingMiniGameData data) {
        BlockPos bobberPos = data.getBobberBlockPos();
        ResourceLocation bobberDimension = data.getBobberDimension();

        // 舊存檔或除錯啟動可能沒有位置資料，才退回玩家位置。
        if (bobberPos == null || bobberDimension == null) {
            bobberPos = player.blockPosition();
            bobberDimension = player.level().dimension().location();
        }

        if (Level.NETHER.location().equals(bobberDimension)) {
            return ResourceLocation.fromNamespaceAndPath(SwingFishing.MOD_ID, "fishing/nether");
        }
        if (Level.END.location().equals(bobberDimension)) {
            return ResourceLocation.fromNamespaceAndPath(SwingFishing.MOD_ID, "fishing/end");
        }
        if (player.serverLevel().getBiome(bobberPos).is(BiomeTags.IS_OCEAN)) {
            return ResourceLocation.fromNamespaceAndPath(SwingFishing.MOD_ID, "fishing/ocean");
        }
        return ResourceLocation.fromNamespaceAndPath(SwingFishing.MOD_ID, "fishing/river");
    }

    /**
     * 獲取玩家手持釣竿的特定附魔等級。
     */
    public static int getFishingRodEnchantmentLevel(ServerPlayer player, net.minecraft.resources.ResourceKey<net.minecraft.world.item.enchantment.Enchantment> enchantment) {
        net.minecraft.world.item.ItemStack mainHand = player.getMainHandItem();
        net.minecraft.world.item.ItemStack offHand = player.getOffhandItem();
        net.minecraft.world.item.ItemStack rod = null;

        if (mainHand.getItem() instanceof net.minecraft.world.item.FishingRodItem) {
            rod = mainHand;
        } else if (offHand.getItem() instanceof net.minecraft.world.item.FishingRodItem) {
            rod = offHand;
        }

        if (rod == null || rod.isEmpty()) return 0;

        var registry = player.level().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
        return net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(
                registry.getOrThrow(enchantment), rod
        );
    }

    private static void removeHook(ServerPlayer player, UUID hookUUID, boolean playSound) {
        if (hookUUID == null) return;

        FishingHook hook = findHook(player, hookUUID);
        if (hook != null) {
            if (playSound) {
                // 播放清脆的收線音效
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.PLAYERS, 1.0F, 1.0F);
            }
            hook.discard();
        }

        // ✨ 關鍵：清空玩家的釣魚狀態，讓玩家可以重新拋竿
        if (player.fishing != null && player.fishing.getUUID().equals(hookUUID)) {
            player.fishing = null;
        }
    }

    private static FishingHook findHook(ServerPlayer player, UUID hookUUID) {
        if (hookUUID == null) {
            return null;
        }
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        var entity = serverLevel.getEntity(hookUUID);
        return entity instanceof FishingHook hook ? hook : null;
    }

    /**
     * 均勻隨機抽取一個 8 方位方向。
     * <p>直接從 {@link SwipeDirection#values()} 均勻抽取，確保 4 個正交方向
     * （UP / DOWN / LEFT / RIGHT）與 4 個對角方向（UP_RIGHT 等）出現機率相同，
     * 避免透過隨機向量 + {@code closestTo} 導致正交方向永遠抽不到。</p>
     */
    private static SwipeDirection randomDirection() {
        SwipeDirection[] values = SwipeDirection.values();
        return values[(int) (Math.random() * values.length)];
    }
}
