package com.swingfishing.client;

import com.swingfishing.network.SwipeInputPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 客戶端專屬事件監聽器。
 */
public final class ClientEvents {

    private static float previousYaw;
    private static float previousPitch;
    private static double previousX;
    private static double previousY;
    private static double previousZ;
    private static net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> previousDimension;
    private static boolean rotationInitialized;

    private ClientEvents() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        // 1. 推進 HUD 倒計時（必須放在 player == null 檢查之前，確保斷線/過渡時也能倒數）
        ClientMiniGameState.tick();

        var player = Minecraft.getInstance().player;
        if (player == null) return;

        var currentDimension = player.level().dimension();

        // 首次登入建立取樣基準
        if (!rotationInitialized) {
            previousYaw = player.getYRot();
            previousPitch = player.getXRot();
            previousX = player.getX();
            previousY = player.getY();
            previousZ = player.getZ();
            previousDimension = currentDimension;
            rotationInitialized = true;
        }

        if (ClientMiniGameState.isActive()) {
            float deltaYaw = Mth.wrapDegrees(player.getYRot() - previousYaw);
            float deltaPitch = player.getXRot() - previousPitch;

            // 偵測傳送 / 重生 / 換維度造成的視角跳變，避免把瞬間的大 delta 當成甩動送出，
            // 被伺服器 isClearlyWrong 誤判為失誤。
            // 判定條件（任一成立即視為跳變）：
            //   1. 維度變更
            //   2. 位置單軸變化 > 2 格（等同 > 40 格/秒，正常移動無法達到）
            //   3. Pitch 單 tick 變化 > 90°（正常甩動最多 ±50°；重生時從 ±90 歸零會觸發）
            double dx = player.getX() - previousX;
            double dy = player.getY() - previousY;
            double dz = player.getZ() - previousZ;
            boolean worldJumped = !currentDimension.equals(previousDimension)
                    || Math.abs(dx) > 2.0D
                    || Math.abs(dy) > 2.0D
                    || Math.abs(dz) > 2.0D
                    || Math.abs(deltaPitch) > 90.0F;

            if (!worldJumped && Math.hypot(deltaYaw, deltaPitch) >= 0.5D) {
                PacketDistributor.sendToServer(new SwipeInputPayload(deltaYaw, deltaPitch));
            }
        }

        // 3. 每 tick 一律更新取樣基準。
        // 「非小遊戲期間」的轉頭 / 移動也在此同步，確保下一局從乾淨基準開始計算 delta。
        previousYaw = player.getYRot();
        previousPitch = player.getXRot();
        previousX = player.getX();
        previousY = player.getY();
        previousZ = player.getZ();
        previousDimension = currentDimension;

        // 4. 監聽「印出狀態」按鍵（改為翻譯鍵）
        while (KeyBindings.DEBUG_PRINT_STATE.consumeClick()) {
            player.sendSystemMessage(Component.translatable(
                    "message.swingfishing.debug.client_state",
                    ClientMiniGameState.isActive(),
                    ClientMiniGameState.getTargetDirection(),
                    ClientMiniGameState.getSuccessCount()
            ));
        }

        // 5. 監聽「發送假封包」按鍵（改為翻譯鍵）
        while (KeyBindings.DEBUG_SEND_SWIPE.consumeClick()) {
            PacketDistributor.sendToServer(new SwipeInputPayload(0.0f, -20.0f));
            player.sendSystemMessage(Component.translatable("message.swingfishing.debug.test_swipe_sent"));
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        var graphics = event.getGuiGraphics();
        int x = minecraft.getWindow().getGuiScaledWidth() / 2;

        // ── 進行中：顯示方向 + 進度 ─────────────────────────────────
        if (ClientMiniGameState.isActive() && ClientMiniGameState.getTargetDirection() != null) {
            Component direction = Component.translatable(
                    ClientMiniGameState.getTargetDirection().getTranslationKey());

            // 判斷是否在冷卻中
            boolean isCooldown = ClientMiniGameState.isCooldownActive();

            // 半透明效果：正常 0xFFFFFF（不透明），冷卻時 0x60FFFFFF（約 37% 透明度）
            int arrowColor = isCooldown ? 0x60FFFFFF : 0xFFFFFF;

            // 如果在冷卻中，在箭頭上方顯示「回正中...」
            if (isCooldown) {
                Component resetHint = Component.translatable("hud.swingfishing.reset_center");
                graphics.drawCenteredString(minecraft.font, resetHint, x, 8, 0xAAAAAA); // 淺灰色
            }

            // 換算成秒數，並保留一位小數
            float secondsRemaining = ClientMiniGameState.getTicksRemaining() / 20.0f;
            String timeStr = String.format("%.1f", secondsRemaining);

            Component progress = Component.translatable("hud.swingfishing.progress",
                    ClientMiniGameState.getSuccessCount(), ClientMiniGameState.getRequiredSuccess(),
                    ClientMiniGameState.getFailedCount(), ClientMiniGameState.getMaxFailed(),
                    timeStr);

            graphics.drawCenteredString(minecraft.font, direction, x, 20, arrowColor); // 使用動態顏色
            graphics.drawCenteredString(minecraft.font, progress, x, 32, 0xFFFF55);
            return;
        }

        // ── 已結束：短暫顯示結果提示 ────────────────────────────────
        if (ClientMiniGameState.getEndMessageTicks() > 0) {
            Component message;
            int color;
            switch (ClientMiniGameState.getLastEndReason()) {
                case CAUGHT -> {
                    message = Component.translatable("message.swingfishing.minigame.caught");
                    color = 0x55FF55;   // 綠色：成功
                }
                case ESCAPED -> {
                    message = Component.translatable("message.swingfishing.minigame.escaped");
                    color = 0xFF5555;   // 紅色：脫鉤
                }
                case ABORTED -> {
                    message = Component.translatable("message.swingfishing.minigame.aborted");
                    color = 0xFFFF55;   // 黃色：中斷
                }
                default -> {
                    return;             // NONE：不應出現，保險跳過
                }
            }
            graphics.drawCenteredString(minecraft.font, message, x, 20, color);
        }
    }
}