package com.swingfishing.item;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import vazkii.patchouli.api.PatchouliAPI;

public class FishBookItem extends Item {
    public FishBookItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // 必須在伺服器端執行
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            // 這裡的 ID 必須是 "swingfishing:fishing_guide"
            ResourceLocation bookId = ResourceLocation.fromNamespaceAndPath("swingfishing", "fishing_guide");

            try {
                PatchouliAPI.get().openBookGUI(serverPlayer, bookId);
            } catch (Exception e) {
                player.sendSystemMessage(Component.literal("打開書本失敗: " + e.getMessage()));
            }
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}