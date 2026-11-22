package com.swingfishing.fishing.game;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.swingfishing.fishing.data.FishingMiniGameData;
import com.swingfishing.fishing.data.ModAttachments;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public class DebugCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("sfdebug")
                .requires(source -> source.hasPermission(2)) // 需要 OP 權限

                // /sfdebug start
                .then(Commands.literal("start").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    FishingMiniGameManager.debugStartGame(player);
                    return 1;
                }))

                // /sfdebug swipe <yaw> <pitch>
                .then(Commands.literal("swipe")
                        .then(Commands.argument("yaw", DoubleArgumentType.doubleArg())
                                .then(Commands.argument("pitch", DoubleArgumentType.doubleArg())
                                        .executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                                            double yaw = DoubleArgumentType.getDouble(ctx, "yaw");
                                            double pitch = DoubleArgumentType.getDouble(ctx, "pitch");

                                            FishingMiniGameManager.handleSwipe(player, yaw, pitch);
                                            return 1;
                                        })
                                )
                        )
                )

                // /sfdebug status
                .then(Commands.literal("status").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    FishingMiniGameData data = player.getData(ModAttachments.FISHING_MINI_GAME.get());

                    String msg = String.format("[Debug] Active: %s | Target: %s | Success: %d | Failed: %d | Ticks: %d",
                            data.isActive(), data.getTargetDirection(), data.getSuccessCount(), data.getFailedCount(), data.getTicksElapsed());
                    player.sendSystemMessage(Component.literal(msg));
                    return 1;
                }))

                // /sfdebug abort
                .then(Commands.literal("abort").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    FishingMiniGameData data = player.getData(ModAttachments.FISHING_MINI_GAME.get());
                    if (data.isActive()) {
                        FishingMiniGameManager.abortGame(player, data, FishingMiniGameManager.AbortReason.CONFIG_DISABLED);
                    }
                    return 1;
                }))
        );
    }
}