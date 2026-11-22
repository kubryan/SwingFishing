package com.swingfishing.fishing.data;

import com.swingfishing.fishing.game.SwipeDirection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.common.util.INBTSerializable;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * 玩家釣魚小遊戲的狀態資料（NeoForge 1.21.1）。
 *
 * <p>此類別為純資料容器，實作 {@link INBTSerializable} 以支援存檔。
 * 所有欄位皆由伺服器端權威讀寫，客戶端不應直接修改。</p>
 *
 * <p><b>資料分類：</b>
 * <ul>
 *   <li><b>單次小遊戲狀態</b>（{@code active}、{@code targetDirection}、
 *       {@code successCount} 等）：由 {@link #start}、{@link #incrementSuccess}、
 *       {@link #incrementFailed} 更新，{@link #clear()} 徹底重置。</li>
 *   <li><b>跨小遊戲持久資料</b>（{@code caughtFish} 魚圖鑑發現集合）：
 *       由 {@link #addCaughtFish} 累積，<b>不受 {@link #clear()} 影響</b>；
 *       僅在玩家資料被刪除時自然消失。</li>
 * </ul>
 * </p>
 */
public class FishingMiniGameData implements INBTSerializable<CompoundTag> {

    // ============================================================
    // 狀態欄位（每次新增／修改欄位時，必須同步更新 NBT 序列化與翻譯檔）
    // ============================================================

    /** 是否正在進行小遊戲。 */
    private boolean active;

    /** 目前要求玩家甩動的目標方向。 */
    private SwipeDirection targetDirection;

    /** 本輪已成功跟上的甩動次數。 */
    private int successCount;

    /** 本輪已累計的失誤次數。 */
    private int failedCount;

    /** 當前方向指令已耗費的 ticks（用於比對 Config 的 swipeTimeoutTicks）。 */
    private int ticksElapsed;

    /** 對應的浮標實體 UUID（用於防呆清理：若浮標消失則強制結束小遊戲）。 */
    private UUID bobberUUID;

    /** 浮標所在方塊位置與維度，用於選擇生態域戰利品表。 */
    private BlockPos bobberBlockPos;
    private ResourceLocation bobberDimension;

    /** 自上次判定以來累積的 Yaw 增量（degrees）。 */
    private double accumulatedYaw;

    /** 自上次判定以來累積的 Pitch 增量（degrees）。 */
    private double accumulatedPitch;

    /** 忽略輸入的剩餘 ticks（用於甩動後的歸位冷卻）。 */
    private int ignoreTicks;

    /**
     * 已發現的魚類 ID 集合（跨小遊戲持久資料，用於魚圖鑑）。
     *
     * <p><b>此欄位不屬於單次小遊戲狀態</b>，因此 {@link #clear()} 不會清理它；
     * 僅在玩家資料被刪除時自然消失。每次釣到新魚時由
     * {@link com.swingfishing.fishing.game.FishingMiniGameManager#grantFishingLoot}
     * 呼叫 {@link #addCaughtFish(String)} 更新。</p>
     *
     * <p>使用 {@link TreeSet} 保證序列化順序穩定（方便測試與存檔 diff）。</p>
     */
    private final Set<String> caughtFish = new TreeSet<>();

    // ============================================================
    // 建構子
    // ============================================================

    public FishingMiniGameData() {
        // 預設建構子供 AttachmentType 的預設值供應器使用。
        clear();
    }

    // ============================================================
    // 狀態操作方法
    // ============================================================

    /**
     * 開始一輪新的小遊戲，重置所有計數並指定目標方向與浮標。
     *
     * @param firstDirection 第一個目標方向
     * @param bobberUUID     對應的浮標實體 UUID
     * @param initialTicks   初始已耗費 ticks（通常傳 0）
     */
    public void start(SwipeDirection firstDirection, UUID bobberUUID, int initialTicks) {
        start(firstDirection, bobberUUID, null, null, initialTicks);
    }

    /** 開始小遊戲並記錄浮標所在方塊，供結算時判定生態域。 */
    public void start(SwipeDirection firstDirection, UUID bobberUUID,
                      BlockPos bobberBlockPos, ResourceLocation bobberDimension, int initialTicks) {
        // 1. 先清空上一局殘留的累積值與冷卻
        resetAccumulation();
        this.ignoreTicks = 0;

        // 2. 賦予新的狀態數據
        this.targetDirection = firstDirection;
        this.successCount = 0;
        this.failedCount = 0;
        this.ticksElapsed = Math.max(0, initialTicks);
        this.bobberUUID = bobberUUID;
        this.bobberBlockPos = bobberBlockPos == null ? null : bobberBlockPos.immutable();
        this.bobberDimension = bobberDimension;

        // 3. 最後才將標記設為活躍（確保 active 為 true 時，所有數據都已就緒）
        this.active = true;
    }

    /**
     * 徹底清理單次小遊戲狀態。在小遊戲結束、脫鉤、玩家死亡、切換維度、關閉功能等情況呼叫。
     *
     * <p><b>注意：{@link #caughtFish} 屬於跨小遊戲持久資料，刻意不在此清理。</b>
     * 若未來新增其他「跨局資料」欄位，也應比照辦理並在此註解列舉。</p>
     */
    public void clear() {
        // 1. 立即標記為非活躍，防止在這瞬間被其他邏輯判定為進行中
        this.active = false;

        // 2. 清空所有單次狀態
        this.targetDirection = null;
        this.successCount = 0;
        this.failedCount = 0;
        this.ticksElapsed = 0;
        this.bobberUUID = null;
        this.bobberBlockPos = null;
        this.bobberDimension = null;

        // 3. 清理累積與冷卻
        resetAccumulation();
        this.ignoreTicks = 0;

        // 4. caughtFish 不動（跨局資料）
    }

    /**
     * 成功次數 +1，並可選擇是否立即切換至下一個目標方向。
     *
     * @param nextDirection 下一個目標方向（若為 null 則保持不變）
     */
    public void incrementSuccess(SwipeDirection nextDirection) {
        if (!active) return;
        this.successCount++;
        if (nextDirection != null) {
            this.targetDirection = nextDirection;
        }
        this.ticksElapsed = 0;
    }

    /**
     * 失誤次數 +1，並重置當前方向的計時。
     * <p>呼叫端應在失誤後檢查是否達到 Config 的 maxFailedSwipes 上限。</p>
     */
    public void incrementFailed() {
        if (!active) return;
        this.failedCount++;
        this.ticksElapsed = 0;
    }

    /**
     * 每 tick 遞增計時器。由伺服器端狀態機在 {@code PlayerTickEvent.Post} 中呼叫。
     *
     * <p><b>冷卻不佔用超時時間：</b>處於 {@code ignoreTicks > 0} 的歸位冷卻期間時，
     * 只遞減冷卻、<b>不遞增</b> {@code ticksElapsed}，讓玩家有完整作答時間。
     * 否則若將 {@code swipeCooldownTicks} 設得比 {@code swipeTimeoutTicks} 還長，
     * 冷卻還沒結束就會先超時失敗。</p>
     */
    public void tickTimer() {
        if (!active) {
            return;
        }
        if (ignoreTicks > 0) {
            ignoreTicks--;
            return;
        }
        ticksElapsed++;
    }

    /**
     * 是否已達到任一結束條件（成功次數達標或失誤次數超標）。
     *
     * @param requiredSuccess 成功次數上限（來自 Config）
     * @param maxFailed       失誤次數上限（來自 Config）
     * @return 若應結束小遊戲則回傳 {@code true}
     */
    public boolean isFinished(int requiredSuccess, int maxFailed) {
        return successCount >= requiredSuccess || failedCount >= maxFailed;
    }

    // ============================================================
    // Getters
    // ============================================================

    public boolean isActive() { return active; }
    public SwipeDirection getTargetDirection() { return targetDirection; }
    public int getSuccessCount() { return successCount; }
    public int getFailedCount() { return failedCount; }
    public int getTicksElapsed() { return ticksElapsed; }
    public UUID getBobberUUID() { return bobberUUID; }
    public BlockPos getBobberBlockPos() { return bobberBlockPos; }
    public ResourceLocation getBobberDimension() { return bobberDimension; }

    // ============================================================
    // 魚圖鑑（跨小遊戲持久資料）
    // ============================================================

    /**
     * 新增一筆已發現魚類。
     *
     * @param fishId 魚的物品 ID（不含命名空間，例如 {@code "agave_bream"}）
     * @return 若這是<b>首次</b>發現該魚則回傳 {@code true}；重複發現回傳 {@code false}
     */
    public boolean addCaughtFish(String fishId) {
        return this.caughtFish.add(fishId);
    }

    /**
     * 取得目前所有已發現魚類 ID 的<b>唯讀</b>快照。
     *
     * <p>供魚圖鑑 GUI 與單元測試使用；呼叫端<b>不可</b>透過此集合修改內部狀態
     * （回傳 {@link Collections#unmodifiableSet} 的視圖）。</p>
     */
    public Set<String> getCaughtFish() {
        return Collections.unmodifiableSet(this.caughtFish);
    }

    /** 是否已發現指定魚類。 */
    public boolean hasCaughtFish(String fishId) {
        return this.caughtFish.contains(fishId);
    }

    // ============================================================
    // INBTSerializable 實作（NeoForge 1.21.1 使用 HolderLookup.Provider）
    // ============================================================

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Active", active);
        tag.putString("TargetDirection", targetDirection != null ? targetDirection.name() : "");
        tag.putInt("SuccessCount", successCount);
        tag.putInt("FailedCount", failedCount);
        tag.putInt("TicksElapsed", ticksElapsed);
        if (bobberUUID != null) {
            tag.putUUID("BobberUUID", bobberUUID);
        }
        if (bobberBlockPos != null) {
            tag.putInt("BobberX", bobberBlockPos.getX());
            tag.putInt("BobberY", bobberBlockPos.getY());
            tag.putInt("BobberZ", bobberBlockPos.getZ());
        }
        if (bobberDimension != null) {
            tag.putString("BobberDimension", bobberDimension.toString());
        }

        // ✨ 魚圖鑑已發現紀錄（跨小遊戲持久資料）
        // 使用 ListTag<StringTag> 而非逗號分隔字串：ID 含逗號時仍安全，
        // 且 NBT 工具與除錯工具能直接展開檢視。
        ListTag fishList = new ListTag();
        for (String id : caughtFish) {
            fishList.add(StringTag.valueOf(id));
        }
        tag.put("CaughtFish", fishList);

        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        this.active = tag.getBoolean("Active");

        // 方向以字串還原；若列舉成員被改名/移除（舊存檔、跨版本），
        // valueOf 會拋 IllegalArgumentException 導致登入崩潰。
        // 因此改為容錯：無法解析時視同「無目標方向」，並強制 active=false，
        // 避免帶著 active=true 卻沒有 targetDirection 的半殘狀態。
        String dirName = tag.getString("TargetDirection");
        SwipeDirection parsedDirection = null;
        if (!dirName.isEmpty()) {
            try {
                parsedDirection = SwipeDirection.valueOf(dirName);
            } catch (IllegalArgumentException e) {
                // 舊存檔 / 列舉改名：無法還原方向，整局作廢
                this.active = false;
            }
        }
        this.targetDirection = parsedDirection;

        this.successCount = tag.getInt("SuccessCount");
        this.failedCount = tag.getInt("FailedCount");
        this.ticksElapsed = tag.getInt("TicksElapsed");
        this.bobberUUID = tag.hasUUID("BobberUUID") ? tag.getUUID("BobberUUID") : null;
        this.bobberBlockPos = tag.contains("BobberX") && tag.contains("BobberY") && tag.contains("BobberZ")
                ? new BlockPos(tag.getInt("BobberX"), tag.getInt("BobberY"), tag.getInt("BobberZ")) : null;
        String dimensionName = tag.getString("BobberDimension");
        this.bobberDimension = dimensionName.isEmpty() ? null : ResourceLocation.tryParse(dimensionName);

        // 魚圖鑑已發現紀錄
        this.caughtFish.clear();
        ListTag fishList = tag.getList("CaughtFish", Tag.TAG_STRING);
        for (int i = 0; i < fishList.size(); i++) {
            String id = fishList.getString(i);
            if (!id.isEmpty()) {
                this.caughtFish.add(id);
            }
        }
    }

    // ============================================================
    // 甩動累積（伺服器端將多個微小 delta 合成一次有效甩動）
    // ============================================================

    /**
     * 累加一次視角輸入。
     * <p>由 {@link com.swingfishing.fishing.game.FishingMiniGameManager#handleSwipe}
     * 在每次收到封包時呼叫。累積值達 {@code MIN_ANGLE_DEGREES} 後才進行方向判定，
     * 避免慢速甩動被逐包丟棄。</p>
     */
    public void accumulateSwipe(double deltaYaw, double deltaPitch) {
        this.accumulatedYaw += deltaYaw;
        this.accumulatedPitch += deltaPitch;
    }

    /** 自上次判定以來累積的 Yaw 增量（degrees）。 */
    public double getAccumulatedYaw() { return accumulatedYaw; }

    /** 自上次判定以來累積的 Pitch 增量（degrees）。 */
    public double getAccumulatedPitch() { return accumulatedPitch; }

    /** 清零累積，準備下一次判定。成功、失敗、模糊地帶、逾時、開始、清理時皆呼叫。 */
    public void resetAccumulation() {
        this.accumulatedYaw = 0.0D;
        this.accumulatedPitch = 0.0D;
    }

    /** 設定冷卻時間，在此期間內收到的甩動封包將被忽略。 */
    public void setIgnoreTicks(int ticks) {
        this.ignoreTicks = Math.max(0, ticks);
    }

    /** 當前是否處於忽略輸入的冷卻狀態。 */
    public boolean isIgnoringInput() {
        return ignoreTicks > 0;
    }
}