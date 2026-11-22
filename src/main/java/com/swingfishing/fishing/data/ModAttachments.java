package com.swingfishing.fishing.data;

import com.swingfishing.SwingFishing;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * 資料附件（AttachmentType）註冊類別（NeoForge 1.21.1）。
 *
 * <p>將 {@link FishingMiniGameData} 掛載到 {@link net.minecraft.world.entity.player.Player}
 * 實體上，供伺服器端狀態機與封包處理器讀寫。</p>
 *
 * <p><b>NeoForge 1.21.1 API 細節：</b>
 * <ul>
 *   <li>使用 {@link DeferredRegister#create(net.minecraft.core.Registry, String)}
 *       搭配 {@link NeoForgeRegistries#ATTACHMENT_TYPES} 建立註冊器。</li>
 *   <li>使用 {@link AttachmentType#serializable(Supplier)} 建立可序列化的附件類型，
 *       其預設值供應器為 {@code FishingMiniGameData::new}。</li>
 *   <li>使用 {@link AttachmentType.Builder#copyOnDeath()} 宣告「玩家死亡重生時保留資料」，
 *       複製機制透過 {@code serializeNBT → deserializeNBT} 完成，
 *       因此 {@link FishingMiniGameData} 的序列化必須涵蓋所有需保留的欄位
 *       （尤其是跨局資料 {@code caughtFish}）。</li>
 *   <li>呼叫 {@code .build()} 完成建構，並以 {@code register(String, Supplier)} 註冊。</li>
 * </ul>
 * </p>
 */
public final class ModAttachments {

    /** 附件類型專用的 DeferredRegister，需在主類別中掛載到 modEventBus。 */
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, SwingFishing.MOD_ID);

    /**
     * 玩家釣魚小遊戲狀態附件。
     *
     * <p>使用 {@code serializable} 以支援存檔；預設值為全新的 {@link FishingMiniGameData}
     * （所有單次狀態欄位已由 {@link FishingMiniGameData#clear()} 重置）。</p>
     *
     * <p>使用 {@code copyOnDeath()} 讓玩家死亡重生時保留附件資料——
     * 特別是跨小遊戲的魚圖鑑紀錄 {@code caughtFish} 不會因重生而遺失。</p>
     */
    public static final Supplier<AttachmentType<FishingMiniGameData>> FISHING_MINI_GAME =
            ATTACHMENT_TYPES.register(
                    "fishing_mini_game",
                    () -> AttachmentType.serializable(FishingMiniGameData::new)
                            .copyOnDeath()
                            .build()
            );

    private ModAttachments() {
        // 工具類別，禁止實例化。
    }
}