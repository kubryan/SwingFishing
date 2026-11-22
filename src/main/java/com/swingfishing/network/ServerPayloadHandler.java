package com.swingfishing.network;

import com.swingfishing.fishing.game.FishingMiniGameManager;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ServerPayloadHandler {

    private ServerPayloadHandler() {
        // 工具類別，禁止實例化。
    }

    public static void handleSwipeInput(SwipeInputPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            // 驗證玩家實體
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            // 拒絕 NaN/無限值與異常大的封包，避免繞過方向判定或造成數值污染。
            if (!Float.isFinite(payload.deltaYaw()) || !Float.isFinite(payload.deltaPitch())
                    || Math.hypot(payload.deltaYaw(), payload.deltaPitch()) > 180.0D) {
                return;
            }
            FishingMiniGameManager.handleSwipe(player, payload.deltaYaw(), payload.deltaPitch());
        });
    }
}
