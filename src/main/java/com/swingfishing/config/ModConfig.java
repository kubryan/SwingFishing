package com.swingfishing.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Swing Fishing 模組的 COMMON 配置定義。
 *
 * <p>此配置在物理客戶端與物理伺服器皆會載入，但不會透過網路同步。
 * 所有與小遊戲規則相關的參數（成功次數、失誤上限、逾時、最小角度）皆由伺服器端權威讀取，
 * 客戶端僅在需要顯示相關資訊時讀取。</p>
 *
 * <p><b>上下游銜接點：</b>
 * <ul>
 *   <li>由 {@code com.swingfishing.SwingFishing} 主類別在建構子中呼叫
 *       {@code modContainer.registerConfig(ModConfig.Type.COMMON, ModConfig.COMMON_SPEC)} 進行註冊。</li>
 *   <li>由 {@code com.swingfishing.fishing.game} 套件中的狀態機與判定邏輯，
 *       透過 {@code ModConfig.ENABLE_HEAD_SWING_FISHING.get()} 等方法即時讀取最新值。</li>
 * </ul>
 * </p>
 */
public final class ModConfig {

    /** COMMON 配置規格，供主類別註冊使用。 */
    public static final ModConfigSpec COMMON_SPEC;

    // ============================================================
    // 欄位定義（每次新增／修改欄位時，必須同步更新此處與翻譯檔）
    // ============================================================

    /**
     * 是否啟用 8 方位視角擺頭釣魚小遊戲。
     * <p>對應 Config 欄位：{@code enableHeadSwingFishing}（boolean，預設 true）。</p>
     */
    public static final ModConfigSpec.BooleanValue ENABLE_HEAD_SWING_FISHING;

    /**
     * 單次釣魚需成功跟上的轉動次數。
     * <p>對應 Config 欄位：{@code requiredSuccessSwipes}（int，預設 5，範圍 1..20）。</p>
     */
    public static final ModConfigSpec.IntValue REQUIRED_SUCCESS_SWIPES;

    /**
     * 允許失誤上限，達到即脫鉤。
     * <p>對應 Config 欄位：{@code maxFailedSwipes}（int，預設 3，範圍 1..10）。</p>
     */
    public static final ModConfigSpec.IntValue MAX_FAILED_SWIPES;

    /**
     * 單一方向指令作答時限，單位為 ticks。
     * <p>對應 Config 欄位：{@code swipeTimeoutTicks}（int，預設 24，範圍 5..200）。</p>
     */
    public static final ModConfigSpec.IntValue SWIPE_TIMEOUT_TICKS;

    /**
     * 判定為有效甩動的最小視角變化角度，單位為 degrees（度）。
     * <p>對應 Config 欄位：{@code minAngleDegrees}（double，預設 15.0，範圍 1.0..90.0）。</p>
     */
    public static final ModConfigSpec.DoubleValue MIN_ANGLE_DEGREES;

    /**
     * 單次甩動後的輸入冷卻時間，單位為 ticks。
     * <p>對應 Config 欄位：{@code swipeCooldownTicks}（int，預設 10，範圍 0..40）。</p>
     */
    public static final ModConfigSpec.IntValue SWIPE_COOLDOWN_TICKS;

    /**
     * 魚餌（Lure）附魔每級增加的作答時限，單位為 ticks。
     * <p>對應 Config 欄位：{@code lureBonusTicksPerLevel}（int，預設 5，範圍 0..100）。</p>
     */
    public static final ModConfigSpec.IntValue LURE_BONUS_TICKS_PER_LEVEL;


    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment(
                "Swing Fishing 小遊戲規則設定。",
                "這些數值由伺服器端權威讀取，客戶端僅在需要時讀取。"
        ).push("swing_fishing");

        ENABLE_HEAD_SWING_FISHING = builder
                .comment(
                        "是否啟用 8 方位視角擺頭釣魚小遊戲。",
                        "設為 false 時，不攔截原版收竿、不發送小遊戲封包、不渲染 HUD。",
                        "預設值：true"
                )
                .translation("swingfishing.configuration.enableHeadSwingFishing")
                .define("enableHeadSwingFishing", true);

        REQUIRED_SUCCESS_SWIPES = builder
                .comment(
                        "單次釣魚需成功跟上的轉動次數。",
                        "範圍：1 ~ 20，預設值：5。"
                )
                .translation("swingfishing.configuration.requiredSuccessSwipes")
                .defineInRange("requiredSuccessSwipes", 5, 1, 20);

        MAX_FAILED_SWIPES = builder
                .comment(
                        "允許失誤上限，達到即脫鉤。",
                        "範圍：1 ~ 10，預設值：3。"
                )
                .translation("swingfishing.configuration.maxFailedSwipes")
                .defineInRange("maxFailedSwipes", 3, 1, 10);

        SWIPE_TIMEOUT_TICKS = builder
                .comment(
                        "單一方向指令作答時限，單位為 ticks。",
                        "範圍：5 ~ 200，預設值：24（約 1.2 秒）。"
                )
                .translation("swingfishing.configuration.swipeTimeoutTicks")
                .defineInRange("swipeTimeoutTicks", 24, 5, 200);

        MIN_ANGLE_DEGREES = builder
                .comment(
                        "判定為有效甩動的最小視角變化角度，單位為 degrees（度）。",
                        "範圍：1.0 ~ 90.0，預設值：15.0。"
                )
                .translation("swingfishing.configuration.minAngleDegrees")
                .defineInRange("minAngleDegrees", 15.0D, 1.0D, 90.0D);

        SWIPE_COOLDOWN_TICKS = builder
                .comment(
                        "單次成功或失誤後，忽略後續視角輸入的冷卻時間（ticks）。",
                        "用於讓玩家將滑鼠拉回正中間，避免歸位動作被判定為反向甩動。",
                        "範圍：0 ~ 40，預設值：10（約 0.5 秒）。"
                )
                .translation("swingfishing.configuration.swipeCooldownTicks")
                .defineInRange("swipeCooldownTicks", 10, 0, 40);

        LURE_BONUS_TICKS_PER_LEVEL = builder
                .comment(
                        "魚餌（Lure）附魔每級增加的作答時限，單位為 ticks。",
                        "設為 0 可完全關閉魚餌對小遊戲的影響。",
                        "注意：與原版釣魚每級減少 5 秒（= 100 ticks）等待不同，本模組預設僅 5 ticks。",
                        "範圍：0 ~ 100，預設值：5。"
                )
                .translation("swingfishing.configuration.lureBonusTicksPerLevel")
                .defineInRange("lureBonusTicksPerLevel", 5, 0, 100);

        builder.pop();

        COMMON_SPEC = builder.build();
    }



    private ModConfig() {
        // 工具類別，禁止實例化。
    }
}