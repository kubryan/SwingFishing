package com.swingfishing.network;

import com.swingfishing.SwingFishing;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class NetworkRegistry {

    private NetworkRegistry() {}

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");

        // Client → Server：甩動輸入
        registrar.playToServer(
                SwipeInputPayload.TYPE,
                SwipeInputPayload.STREAM_CODEC,
                (payload, context) -> {
                    com.swingfishing.network.ServerPayloadHandler.handleSwipeInput(payload, context);
                }
        );

        // Server → Client：小遊戲狀態同步
        registrar.playToClient(
                MiniGameSyncPayload.TYPE,
                MiniGameSyncPayload.STREAM_CODEC,
                (payload, context) -> {
                    com.swingfishing.network.ClientPayloadHandler.handleSync(payload, context);
                }
        );
    }
}