package com.swingfishing;

import com.mojang.logging.LogUtils;
import com.swingfishing.config.ModConfig;
import com.swingfishing.fishing.data.ModAttachments;
import com.swingfishing.fishing.game.DebugCommands;
import com.swingfishing.fishing.game.FishingGameEvents;
import com.swingfishing.fishing.game.FishingMiniGameManager;
import com.swingfishing.network.NetworkClientNotifier;
import com.swingfishing.network.NetworkRegistry;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig.Type;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * SwingFishing 主模組類別（Minecraft 1.21.1 / NeoForge 1.21.1）。
 * 負責模組初始化、設定檔註冊、事件匯流排掛載與 Client/Server 端點隔離。
 *
 * <p><b>注意：</b>本類別匯入的 {@link ModConfig} 為 NeoForge 的
 * {@code net.neoforged.fml.config.ModConfig}，僅用於其 {@code Type} 列舉；
 * 本模組自有的設定規格類別請以全限定名
 * {@code com.swingfishing.config.ModConfig} 引用，避免命名衝突。</p>
 */
@Mod(SwingFishing.MOD_ID)
public class SwingFishing {

    /** 模組唯一識別碼，需與 neoforge.mods.toml 與資源資料夾名稱一致。 */
    public static final String MOD_ID = "swingfishing";

    /** 模組共用日誌紀錄器。 */
    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * NeoForge 1.21.1 標準主類別建構子。
     *
     * @param modEventBus  模組專屬事件匯流排（用於註冊 DeferredRegister、設定檔變更、Payload 註冊等）
     * @param modContainer 模組容器（用於註冊 ModConfigSpec 等設定檔）
     */
    public SwingFishing(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Initializing {} mod on NeoForge 1.21.1", MOD_ID);

        // 1. 設定檔註冊 (Config)
        modContainer.registerConfig(
                Type.COMMON,
                ModConfig.COMMON_SPEC
        );

        // 2. 遊戲內容註冊 (DeferredRegisters)
        ModAttachments.ATTACHMENT_TYPES.register(modEventBus);
        com.swingfishing.item.ModItems.ITEMS.register(modEventBus);
        com.swingfishing.item.ModCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);

        // 3. Mod 事件總線監聽 (Mod Event Bus) - 用於生命週期與網路
        modEventBus.addListener(NetworkRegistry::register);

        // 4. 遊戲運行期事件監聽 (NeoForge Event Bus) - 用於遊戲邏輯與指令
        NeoForge.EVENT_BUS.register(FishingGameEvents.class);
        NeoForge.EVENT_BUS.register(DebugCommands.class);

        // 5. 初始化狀態機的網路通知器 (Singleton Injection)
        FishingMiniGameManager.setClientNotifier(new NetworkClientNotifier());

        // 6. 客戶端專屬初始化 (Client-only Initialization)
        if (FMLEnvironment.dist == Dist.CLIENT) {
            initClient(modEventBus);
        }
    }

    /**
     * 僅於 Dist.CLIENT 執行的初始化方法。
     * 嚴禁在此直接呼叫 Server 獨有類別；Client 渲染與輸入事件應放在 com.swingfishing.client 套件中。
     *
     * @param modEventBus 模組專屬事件匯流排
     */
    private void initClient(IEventBus modEventBus) {
        LOGGER.debug("Registering client-side hooks for {}", MOD_ID);

        // 註冊按鍵綁定
        modEventBus.addListener((net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent event) -> {
            event.register(com.swingfishing.client.KeyBindings.DEBUG_PRINT_STATE);
            event.register(com.swingfishing.client.KeyBindings.DEBUG_SEND_SWIPE);
        });

        // 註冊客戶端 Tick 事件（用於監聽按鍵）
        NeoForge.EVENT_BUS.register(com.swingfishing.client.ClientEvents.class);
    }
}