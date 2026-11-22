package com.swingfishing.client;

import com.swingfishing.fishing.game.SwipeDirection;
import com.swingfishing.network.MiniGameSyncPayload;

/**
 * 客戶端小遊戲狀態快取（NeoForge 1.21.1）。
 *
 * <p>僅儲存最近一次從伺服器同步過來的狀態，供 HUD 渲染與客戶端邏輯讀取。
 * <b>不包含任何伺服器專屬類別引用</b>，確保 Dedicated Server 載入時不會
 * 觸發 {@code NoClassDefFoundError}。</p>
 *
 * <p>所有欄位為 {@code static volatile}，因為寫入發生在網路執行緒
 * （透過 {@code enqueueWork} 回到主執行緒前可能已被其他執行緒讀取），
 * 但實際讀寫仍應在主執行緒中進行。</p>
 */
public final class ClientMiniGameState {

    /** 結束後提示訊息的顯示時長（ticks）。40 ticks = 2 秒。 */
    private static final int END_MESSAGE_DURATION_TICKS = 40;

    private static volatile boolean active = false;
    private static volatile SwipeDirection targetDirection = null;
    private static volatile int successCount = 0;
    private static volatile int requiredSuccess = 5;
    private static volatile int failedCount = 0;
    private static volatile int maxFailed = 3;
    private static volatile MiniGameSyncPayload.EndReason lastEndReason =
            MiniGameSyncPayload.EndReason.NONE;

    private static volatile int clientTicksRemaining = 0;

    private static final int COOLDOWN_TICKS = 10; // 預設 0.5 秒
    private static volatile int clientCooldownTicks = 0;


    /** 結束提示剩餘顯示 ticks；{@code > 0} 時 HUD 顯示 {@link #lastEndReason} 對應的提示。 */
    private static volatile int endMessageTicks = 0;

    private ClientMiniGameState() {
        // 工具類別，禁止實例化。
    }

    /**
     * 由 {@code ClientPayloadHandler} 在收到同步封包時呼叫。
     */
    public static void update(MiniGameSyncPayload payload) {
        // 先快取舊計數，用於偵測「成功/失誤次數增加」以觸發本地冷卻提示
        int oldSuccess = successCount;
        int oldFailed = failedCount;
        boolean countIncreased = payload.successCount() > oldSuccess
                || payload.failedCount() > oldFailed;

        active = payload.active();
        targetDirection = payload.targetDirection();
        successCount = payload.successCount();
        requiredSuccess = payload.requiredSuccess();
        failedCount = payload.failedCount();
        maxFailed = payload.maxFailed();
        lastEndReason = payload.endReason();
        clientTicksRemaining = payload.ticksRemaining();

        if (countIncreased) {
            clientCooldownTicks = COOLDOWN_TICKS;
        }

        if (payload.active()) {
            // 新一輪開始：清除舊的結束提示，避免殘留干擾
            endMessageTicks = 0;
        } else if (payload.endReason() != MiniGameSyncPayload.EndReason.NONE) {
            // 結束：啟動倒計時，讓 HUD 短暫顯示結果
            endMessageTicks = END_MESSAGE_DURATION_TICKS;
        }
    }

    /**
     * 每客戶端 tick 由 {@code ClientEvents.onClientTick} 呼叫，
     * 遞減結束提示剩餘顯示時間。
     */
    public static void tick() {
        if (endMessageTicks > 0) endMessageTicks--;
        if (active && clientTicksRemaining > 0) clientTicksRemaining--;

        // 遞減本地冷卻
        if (clientCooldownTicks > 0) {
            clientCooldownTicks--;
        }
    }

    // ============================================================
    // Getters（供 HUD 渲染器與客戶端邏輯使用）
    // ============================================================

    public static boolean isActive() { return active; }
    public static SwipeDirection getTargetDirection() { return targetDirection; }
    public static int getSuccessCount() { return successCount; }
    public static int getRequiredSuccess() { return requiredSuccess; }
    public static int getFailedCount() { return failedCount; }
    public static int getMaxFailed() { return maxFailed; }
    public static int getTicksRemaining() { return clientTicksRemaining; }
    public static MiniGameSyncPayload.EndReason getLastEndReason() { return lastEndReason; }
    public static boolean isCooldownActive() {return clientCooldownTicks > 0;}

    /** 結束提示剩餘顯示 ticks；{@code > 0} 表示應在 HUD 顯示結束訊息。 */
    public static int getEndMessageTicks() { return endMessageTicks; }

}