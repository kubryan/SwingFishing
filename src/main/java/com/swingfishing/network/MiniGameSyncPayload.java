package com.swingfishing.network;

import com.swingfishing.SwingFishing;
import com.swingfishing.fishing.game.SwipeDirection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 伺服器 → 客戶端的小遊戲狀態同步封包（NeoForge 1.21.1）。
 *
 * <p>將伺服器端權威狀態完整同步至客戶端，供 HUD 渲染與客戶端邏輯使用。
 * 每次狀態變化（開始、成功、失敗、逾時、結束）都會發送一次。</p>
 *
 * @param active           小遊戲是否進行中
 * @param targetDirection  目前目標方向（{@code active == false} 時可為 {@code null}）
 * @param successCount     已成功次數
 * @param requiredSuccess  成功次數上限
 * @param failedCount      已失誤次數
 * @param maxFailed        失誤次數上限
 * @param ticksRemaining   當前方向剩餘 ticks（{@code active == false} 時為 0）
 * @param endReason        結束原因（{@code active == true} 時為 {@link EndReason#NONE}）
 */
public record MiniGameSyncPayload(
        boolean active,
        SwipeDirection targetDirection,
        int successCount,
        int requiredSuccess,
        int failedCount,
        int maxFailed,
        int ticksRemaining,
        EndReason endReason
) implements CustomPacketPayload {

    /** 小遊戲結束原因列舉。 */
    public enum EndReason {
        /** 未結束（小遊戲進行中，或從未開始）。 */
        NONE,
        /** 成功釣起魚。 */
        CAUGHT,
        /** 魚脫鉤跑掉（失誤次數達上限）。 */
        ESCAPED,
        /** 因外部條件中斷（收竿、死亡、換維度、浮標消失等）。 */
        ABORTED
    }

    public static final CustomPacketPayload.Type<MiniGameSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(SwingFishing.MOD_ID, "minigame_sync")
            );

    /**
     * StreamCodec：手動依序編碼 8 個欄位（繞過 composite 的 6 參數上限）。
     * <p>{@link SwipeDirection} 以 {@code ordinal()} 整數編碼，{@code null} 編碼為 -1。
     * {@link EndReason} 以 {@code ordinal()} 整數編碼。</p>
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, MiniGameSyncPayload> STREAM_CODEC =
            StreamCodec.of(
                    // Encode：將 payload 寫入 buf
                    (buf, payload) -> {
                        buf.writeBoolean(payload.active());
                        buf.writeVarInt(payload.targetDirection() == null ? -1 : payload.targetDirection().ordinal());
                        buf.writeVarInt(payload.successCount());
                        buf.writeVarInt(payload.requiredSuccess());
                        buf.writeVarInt(payload.failedCount());
                        buf.writeVarInt(payload.maxFailed());
                        buf.writeVarInt(payload.ticksRemaining());
                        buf.writeVarInt(payload.endReason().ordinal());
                    },
                    // Decode：從 buf 讀取並建構 payload
                    buf -> {
                        boolean active = buf.readBoolean();
                        int targetDirOrd = buf.readVarInt();
                        int successCount = buf.readVarInt();
                        int requiredSuccess = buf.readVarInt();
                        int failedCount = buf.readVarInt();
                        int maxFailed = buf.readVarInt();
                        int ticksRemaining = buf.readVarInt();
                        int endReasonOrd = buf.readVarInt();

                        return new MiniGameSyncPayload(
                                active, targetDirOrd, successCount, requiredSuccess,
                                failedCount, maxFailed, ticksRemaining, endReasonOrd
                        );
                    }
            );

    /**
     * 自訂建構子：從整數還原 {@link SwipeDirection} 與 {@link EndReason}。
     *
     * <p>由 {@link #STREAM_CODEC} 的解碼 lambda 呼叫，將 wire 上的整數 ordinal
     * 轉回列舉。若收到的 ordinal 超出合法範圍（惡意封包、版本不匹配、
     * 未來列舉成員變動），一律降級為安全預設值，避免客戶端
     * {@link ArrayIndexOutOfBoundsException} 崩潰：</p>
     * <ul>
     *   <li>非法 {@code targetDirectionOrdinal} → {@code null}</li>
     *   <li>非法 {@code endReasonOrdinal} → {@link EndReason#NONE}</li>
     * </ul>
     */
    public MiniGameSyncPayload(
            boolean active,
            int targetDirectionOrdinal,
            int successCount,
            int requiredSuccess,
            int failedCount,
            int maxFailed,
            int ticksRemaining,
            int endReasonOrdinal
    ) {
        this(
                active,
                decodeDirection(targetDirectionOrdinal),
                successCount,
                requiredSuccess,
                failedCount,
                maxFailed,
                ticksRemaining,
                decodeEndReason(endReasonOrdinal)
        );
    }

    /** 將 wire 上的 ordinal 安全還原為 {@link SwipeDirection}；非法值回傳 {@code null}。 */
    private static SwipeDirection decodeDirection(int ordinal) {
        SwipeDirection[] values = SwipeDirection.values();
        return (ordinal >= 0 && ordinal < values.length) ? values[ordinal] : null;
    }

    /** 將 wire 上的 ordinal 安全還原為 {@link EndReason}；非法值回傳 {@link EndReason#NONE}。 */
    private static EndReason decodeEndReason(int ordinal) {
        EndReason[] values = EndReason.values();
        return (ordinal >= 0 && ordinal < values.length) ? values[ordinal] : EndReason.NONE;
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}