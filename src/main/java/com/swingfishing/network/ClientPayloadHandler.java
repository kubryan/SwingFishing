package com.swingfishing.network;

import com.swingfishing.client.ClientMiniGameState;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ClientPayloadHandler {

    private ClientPayloadHandler() {
        // 工具類別，禁止實例化。
    }

    public static void handleSync(MiniGameSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientMiniGameState.update(payload));
    }
}