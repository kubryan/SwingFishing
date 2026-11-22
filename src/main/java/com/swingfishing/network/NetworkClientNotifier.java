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
        int required = data.getRolledRarity() != null
                ? data.getRolledRarity().requiredSuccess
                : ModConfig.REQUIRED_SUCCESS_SWIPES.get();
        int maxFailed = data.getRolledRarity() != null
                ? data.getRolledRarity().maxFailed
                : ModConfig.MAX_FAILED_SWIPES.get();
        send(player, true, direction,
                data.getSuccessCount(), required,
                data.getFailedCount(), maxFailed,
                MiniGameSyncPayload.EndReason.NONE);
    }

    @Override
    public void onDirectionChanged(ServerPlayer player, SwipeDirection nextDirection) {
        var data = player.getData(com.swingfishing.fishing.data.ModAttachments.FISHING_MINI_GAME.get());
        int required = data.getRolledRarity() != null
                ? data.getRolledRarity().requiredSuccess
                : ModConfig.REQUIRED_SUCCESS_SWIPES.get();
        int maxFailed = data.getRolledRarity() != null
                ? data.getRolledRarity().maxFailed
                : ModConfig.MAX_FAILED_SWIPES.get();
        send(player, true, nextDirection,
                data.getSuccessCount(), required,
                data.getFailedCount(), maxFailed,
                MiniGameSyncPayload.EndReason.NONE);
    }

    @Override
    public void onSuccess(ServerPlayer player, int successCount, int requiredSuccess) {
        var data = player.getData(com.swingfishing.fishing.data.ModAttachments.FISHING_MINI_GAME.get());
        int maxFailed = data.getRolledRarity() != null
                ? data.getRolledRarity().maxFailed
                : ModConfig.MAX_FAILED_SWIPES.get();
        send(player, data.isActive(), data.getTargetDirection(),
                successCount, requiredSuccess,
                data.getFailedCount(), maxFailed,
                MiniGameSyncPayload.EndReason.NONE);
    }

    @Override
    public void onFailure(ServerPlayer player, int failedCount, int maxFailed) {
        var data = player.getData(com.swingfishing.fishing.data.ModAttachments.FISHING_MINI_GAME.get());
        int required = data.getRolledRarity() != null
                ? data.getRolledRarity().requiredSuccess
                : ModConfig.REQUIRED_SUCCESS_SWIPES.get();
        send(player, data.isActive(), data.getTargetDirection(),
                data.getSuccessCount(), required,
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
