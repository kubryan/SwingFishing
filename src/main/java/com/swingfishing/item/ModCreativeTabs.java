package com.swingfishing.item;

import com.swingfishing.SwingFishing;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 創造模式物品欄分類註冊。
 */
public final class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, SwingFishing.MOD_ID);

    // 建立一個專屬的「甩竿釣魚」分類
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> SWING_FISHING_TAB =
            CREATIVE_MODE_TABS.register("fish_tab", () -> CreativeModeTab.builder()
                    // 分類標題（需在語言檔中定義）
                    .title(Component.translatable("itemGroup.swingfishing.fish_tab"))
                    // 分類的圖示（使用第一隻魚：龍舌蘭鯛魚）
                    .icon(() -> new ItemStack(ModItems.AGAVE_BREAM.get()))
                    // 將所有生成的魚加入此分類
                    .displayItems((parameters, output) -> {
                        ModItems.ITEMS.getEntries().forEach(item -> output.accept(item.get()));
                    })
                    .build());

    private ModCreativeTabs() {}
}