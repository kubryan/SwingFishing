package com.swingfishing.network;

import com.swingfishing.SwingFishing;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 客戶端 → 伺服器的甩動輸入封包（NeoForge 1.21.1）。
 *
 * <p>攜帶本次取樣的視角增量 {@code deltaYaw} 與 {@code deltaPitch}，
 * 單位為 degrees（度）。正值約定：{@code deltaYaw > 0} = 向右轉，
 * {@code deltaPitch > 0} = 往下看。</p>
 *
 * <p><b>發送端：</b>{@code com.swingfishing.client} 套件的視角取樣器。
 * <b>接收端：</b>{@link ServerPayloadHandler}。</p>
 *
 * @param deltaYaw   Yaw 增量（degrees）
 * @param deltaPitch Pitch 增量（degrees）
 */
public record SwipeInputPayload(float deltaYaw, float deltaPitch) implements CustomPacketPayload {

    /** 封包唯一識別碼，命名空間固定為 {@code swingfishing}。 */
    public static final CustomPacketPayload.Type<SwipeInputPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(SwingFishing.MOD_ID, "swipe_input")
            );

    /**
     * StreamCodec：依序編碼兩個 float。
     * <p>{@code StreamCodec.composite} 會依序讀寫每個欄位，
     * 最後以 {@code SwipeInputPayload::new} 建構新實例。</p>
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, SwipeInputPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.FLOAT, SwipeInputPayload::deltaYaw,
                    ByteBufCodecs.FLOAT, SwipeInputPayload::deltaPitch,
                    SwipeInputPayload::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}