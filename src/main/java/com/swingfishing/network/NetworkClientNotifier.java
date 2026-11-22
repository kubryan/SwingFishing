package com.swingfishing.network;

import com.swingfishing.config.ModConfig;
import com.swingfishing.fishing.game.FishingMiniGameManager;
import com.swingfishing.fishing.game.SwipeDirection;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 實作 {@link FishingMiniGameManager.ClientNotifier}，透過網路封包將
 * 伺服器端狀態同步至客戶端。
 *
 * <p>此類別應在主類別初始化時注入：
 * {@code FishingMiniGameManager.setClientNotifier(new NetworkClientNotifier());}</p>
 */
public final class NetworkClientNotifier implements FishingMiniGameManager.ClientNotifier {

    @Override
    public void onGameStarted(ServerPlayer player, SwipeDirection direction) {
        var data = player.getData(com.swingfishing.fishing.data.ModAttachments.FISHING_MINI_GAME.get());
        send(player, true, direction,
                data.getSuccessCount(), ModConfig.REQUIRED_SUCCESS_SWIPES.get(),
                data.getFailedCount(), ModConfig.MAX_FAILED_SWIPES.get(),
                MiniGameSyncPayload.EndReason.NONE);
    }

    @Override
    public void onDirectionChanged(ServerPlayer player, SwipeDirection nextDirection) {
        // 方向變更時，成功次數已在 FishingMiniGameData 中遞增，
        // 但此介面沒有提供計數參數；從玩家的 Attachment 讀取最新值。
        var data = player.getData(com.swingfishing.fishing.data.ModAttachments.FISHING_MINI_GAME.get());
        send(player, true, nextDirection,
                data.getSuccessCount(), ModConfig.REQUIRED_SUCCESS_SWIPES.get(),
                data.getFailedCount(), ModConfig.MAX_FAILED_SWIPES.get(),
                MiniGameSyncPayload.EndReason.NONE);
    }

    @Override
    public void onSuccess(ServerPlayer player, int successCount, int requiredSuccess) {
        // 成功但未換方向（例如成功後立即達標）時，發送不含方向變更的同步。
        var data = player.getData(com.swingfishing.fishing.data.ModAttachments.FISHING_MINI_GAME.get());
        send(player, data.isActive(), data.getTargetDirection(),
                successCount, requiredSuccess,
                data.getFailedCount(), ModConfig.MAX_FAILED_SWIPES.get(),
                MiniGameSyncPayload.EndReason.NONE);
    }

    @Override
    public void onFailure(ServerPlayer player, int failedCount, int maxFailed) {
        var data = player.getData(com.swingfishing.fishing.data.ModAttachments.FISHING_MINI_GAME.get());
        send(player, data.isActive(), data.getTargetDirection(),
                data.getSuccessCount(), ModConfig.REQUIRED_SUCCESS_SWIPES.get(),
                failedCount, maxFailed,
                MiniGameSyncPayload.EndReason.NONE);
    }

    @Override
    public void onGameEnded(ServerPlayer player, MiniGameSyncPayload.EndReason reason) {
        send(player, false, null, 0, 0, 0, 0, reason);
    }

    // ============================================================
    // 輔助方法
    // ============================================================

    private static void send(
            ServerPlayer player,
            boolean active,
            SwipeDirection direction,
            int successCount,
            int requiredSuccess,
            int failedCount,
            int maxFailed,
            MiniGameSyncPayload.EndReason reason
    ) {
        int ticksRemaining = 0;
        if (active) {
            var data = player.getData(com.swingfishing.fishing.data.ModAttachments.FISHING_MINI_GAME.get());
            // 與 FishingMiniGameManager.tickPlayer 使用完全相同的公式，避免兩邊漂移
            int timeout = FishingMiniGameManager.computeTimeoutTicks(player);
            ticksRemaining = Math.max(0, timeout - data.getTicksElapsed());
        }
        PacketDistributor.sendToPlayer(player,
                new MiniGameSyncPayload(
                        active, direction, successCount, requiredSuccess,
                        failedCount, maxFailed, ticksRemaining, reason
                )
        );
    }
}
