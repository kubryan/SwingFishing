package com.swingfishing.fishing.game;

import com.swingfishing.fishing.data.FishingMiniGameData;
import com.swingfishing.fishing.data.ModAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.FishingHook;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 釣魚小遊戲的伺服器端事件協調類別（NeoForge 1.21.1）。
 *
 * <p>監聽原版釣魚與玩家生命週期事件，並轉發給
 * {@link FishingMiniGameManager} 處理。</p>
 *
 * <p>此類別應註冊到 {@code NeoForge.EVENT_BUS}（遊戲執行期事件匯流排），
 * 而非 {@code modEventBus}。</p>
 */
public final class FishingGameEvents {

    private FishingGameEvents() {
        // 工具類別，禁止實例化。
    }

    // ============================================================
    // 魚上鉤：攔截原版戰利品，啟動小遊戲
    // ============================================================

    @SubscribeEvent
    public static void onItemFished(ItemFishedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        // 僅在啟用時攔截；否則讓原版邏輯正常發放戰利品。
        if (!com.swingfishing.config.ModConfig.ENABLE_HEAD_SWING_FISHING.get()) {
            return;
        }

        FishingHook hook = event.getHookEntity();
        if (hook == null) {
            return;
        }

        // 取消原版戰利品發放，由小遊戲結算時手動發放。
        event.setCanceled(true);

        FishingMiniGameManager.startGame(player, hook);
    }

    // ============================================================
    // 每 tick：推進計時與防呆檢查
    // ============================================================

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        FishingMiniGameData data = player.getData(ModAttachments.FISHING_MINI_GAME.get());
        if (!data.isActive()) {
            return;
        }

        FishingMiniGameManager.tickPlayer(player);
    }

    // ============================================================
    // 防呆：玩家死亡
    // ============================================================

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        FishingMiniGameData data = player.getData(ModAttachments.FISHING_MINI_GAME.get());
        if (data.isActive()) {
            FishingMiniGameManager.abortGame(player, data,
                    FishingMiniGameManager.AbortReason.PLAYER_DEATH);
        }
    }

    // ============================================================
    // 防呆：切換維度 / 登出（PlayerEvent.Clone 涵蓋重生與維度切換）
    // ============================================================

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        // 使用「原始玩家」檢查，而非新玩家實體：
        // PlayerEvent.Clone 的 getEntity() 回傳的是複製後的新玩家，其 attachment
        // 從原玩家複製或初始化而來，直接查 isActive() 時機太晚且不直覺。
        //
        // 這裡只做「清理舊玩家的單次小遊戲狀態」：
        // - 死亡路徑：onPlayerDeath 已先 abortGame 清過，此處 isActive() == false，
        //   是 no-op，可作為保險。
        // - 跨維度路徑：NeoForge 在複製 attachment 前觸發本事件，若不清理，
        //   新玩家會短暫帶著 active=true 但沒有對應浮標的殘留狀態。
        //
        // 刻意「不」呼叫 FishingMiniGameManager.abortGame()，因為舊玩家在此時
        // 可能已無法安全使用 level() 或接收封包；abort 的音效與 HUD 通知
        // 由 onPlayerDeath / onPlayerChangedDimension 各別負責。
        if (!(event.getOriginal() instanceof ServerPlayer oldPlayer)) {
            return;
        }
        FishingMiniGameData data = oldPlayer.getData(ModAttachments.FISHING_MINI_GAME.get());
        if (data.isActive()) {
            data.clear();
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        FishingMiniGameData data = player.getData(ModAttachments.FISHING_MINI_GAME.get());
        if (data.isActive()) {
            FishingMiniGameManager.abortGame(player, data,
                    FishingMiniGameManager.AbortReason.DIMENSION_CHANGE);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        FishingMiniGameData data = player.getData(ModAttachments.FISHING_MINI_GAME.get());
        if (data.isActive()) {
            FishingMiniGameManager.abortGame(player, data,
                    FishingMiniGameManager.AbortReason.LOGOUT);
        }
    }

    // ============================================================
    // 防呆：小遊戲期間禁止玩家使用釣竿或右鍵干擾
    // ============================================================
    @SubscribeEvent
    public static void onRightClickItem(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        FishingMiniGameData data = player.getData(ModAttachments.FISHING_MINI_GAME.get());
        if (data.isActive()) {
            // 如果玩家手裡拿的是釣竿，直接取消右鍵事件（阻止重新拋竿）
            if (event.getItemStack().getItem() instanceof net.minecraft.world.item.FishingRodItem) {
                event.setCanceled(true);
            }
        }
    }
}
