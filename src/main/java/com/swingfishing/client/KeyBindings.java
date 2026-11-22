package com.swingfishing.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/**
 * 客戶端按鍵綁定定義。
 */
public final class KeyBindings {

    /** 測試用：印出當前小遊戲狀態。預設綁定為 'J' 鍵。 */
    public static final KeyMapping DEBUG_PRINT_STATE = new KeyMapping(
            "key.swingfishing.debug_print_state",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J,
            "key.categories.swingfishing"
    );

    /** 測試用：發送假的上甩封包。預設綁定為 'K' 鍵。 */
    public static final KeyMapping DEBUG_SEND_SWIPE = new KeyMapping(
            "key.swingfishing.debug_send_swipe",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            "key.categories.swingfishing"
    );

    private KeyBindings() {}
}