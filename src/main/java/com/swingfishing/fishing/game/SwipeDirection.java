package com.swingfishing.fishing.game;

import com.swingfishing.config.ModConfig;

/**
 * 8 方位甩動方向（Minecraft 1.21.1 / NeoForge 1.21.1）。
 *
 * <h3>Minecraft 視角變化正負號約定</h3>
 * <ul>
 *   <li><b>Yaw（左右轉動）</b>：以角度（degrees）表示，範圍 -180° ~ +180°。
 *       從上方俯視時，<b>Yaw 增加 = 向右轉（順時針）</b>，<b>Yaw 減少 = 向左轉（逆時針）</b>。
 *       0° 面向南方（+Z），-90° 面向東方（+X），+90° 面向西方（-X）。</li>
 *   <li><b>Pitch（上下俯仰）</b>：以角度（degrees）表示，範圍 -90° ~ +90°。
 *       <b>Pitch 增加 = 往下看</b>，<b>Pitch 減少 = 往上���</b>。0° 為水平。</li>
 * </ul>
 *
 * <h3>增量轉方向比對公式</h3>
 * <p>給定一次視角增量 {@code (deltaYaw, deltaPitch)}（單位皆為 degrees），
 * 先將其映射到以「上（UP）」為 0°、順時針遞增的角度系統：</p>
 * <pre>{@code
 * double angle = Math.toDegrees(Math.atan2(deltaYaw, -deltaPitch));
 * // 規範化到 [0, 360)
 * angle = (angle % 360 + 360) % 360;
 * }</pre>
 * <p>此角度與各方向的基準角度比較（見 {@link #baseAngleDegrees}），
 * 角距離 {@code d = abs(wrapDegrees180(targetAngle - angle))} 即為偏離目標方向的角度（degrees）。
 * 當 {@code d <= MAX_DEVIATION_DEGREES}（22.5°）且增量向量長度
 * {@code sqrt(deltaYaw² + deltaPitch²) >= MIN_ANGLE_DEGREES} 時，視為「有效甩動」；
 * 當 {@code d > CLEARLY_WRONG_DEVIATION_DEGREES}（90°）時，視為「明顯反向／錯誤甩動」；
 * 介於兩者之間為「模糊地帶」，不計成功也不計失誤。
 *
 * <p><b>注意：</b>方向基準角度是以 UP=0°、順時針每 45° 遞增定義的，
 * 與 Minecraft 原始 Yaw 角度無直接對應關係，僅用於增量向量的方向比對。</p>
 */
public enum SwipeDirection {

    /** 上（Pitch 減少方向）。基準角度 0°。 */
    UP(0.0, "up"),

    /** 右上（Yaw 增加 + Pitch 減少）。基準角度 45°。 */
    UP_RIGHT(45.0, "up_right"),

    /** 右（Yaw 增加方向）。基準角度 90°。 */
    RIGHT(90.0, "right"),

    /** 右下（Yaw 增加 + Pitch 增加）。基準角度 135°。 */
    DOWN_RIGHT(135.0, "down_right"),

    /** 下（Pitch 增加方向）。基準角度 180°。 */
    DOWN(180.0, "down"),

    /** 左下（Yaw 減少 + Pitch 增加）。基準角度 225°。 */
    DOWN_LEFT(225.0, "down_left"),

    /** 左（Yaw 減少方向）。基準角度 270°。 */
    LEFT(270.0, "left"),

    /** 左上（Yaw 減少 + Pitch 減少）。基準角度 315°。 */
    UP_LEFT(315.0, "up_left");

    /** 相鄰方向之間的基準角度差（degrees）。8 方向均分 360°。 */
    public static final double ANGLE_STEP_DEGREES = 45.0;

    /**
     * 有效甩動允許的最大角距離（degrees）。
     * <p>8 方向等分 360°，每個方向名義上覆蓋 ±22.5°（相鄰基準角差 45° 的一半）。
     * 此值即為硬邊界：角距離 {@code <= 22.5°} 視為匹配該方向；
     * 超過 22.5° 但不超過 90° 落入「模糊地帶」，玩家可重新甩動；
     * 超過 90° 由 {@link #isClearlyWrong(double, double)} 判為明顯反向。</p>
     */
    public static final double MAX_DEVIATION_DEGREES = 22.5;

    /**
     * 判定為「明顯反向／錯誤甩動」的最小角距離（degrees）。
     * 超過此值表示增量方向與目標方向夾角大於 90°，落入對向象限。
     */
    public static final double CLEARLY_WRONG_DEVIATION_DEGREES = 90.0;

    private final double baseAngleDegrees;
    private final String translationKey;

    SwipeDirection(double baseAngleDegrees, String path) {
        this.baseAngleDegrees = baseAngleDegrees;
        this.translationKey = "hud.swingfishing.direction." + path;
    }

    /**
     * 此方向的基準角度（degrees），以 UP=0°、順時針遞增。
     *
     * @return 基準角度，範圍 [0, 360)
     */
    public double baseAngleDegrees() {
        return baseAngleDegrees;
    }

    /**
     * HUD 顯示用的翻譯鍵。
     * 對應 {@code Component.translatable(getTranslationKey())}。
     *
     * @return 翻譯鍵，例如 {@code "hud.swingfishing.direction.up_right"}
     */
    public String getTranslationKey() {
        return translationKey;
    }

    // ============================================================
    // 核心判定方法
    // ============================================================

    /**
     * 判斷給定的視角增量是否「有效符合」此目標方向。
     * 門檻即時取自 {@link ModConfig#MIN_ANGLE_DEGREES}。
     *
     * @param deltaYaw   Yaw 增量（degrees；正值 = 向右轉）
     * @param deltaPitch Pitch 增量（degrees；正值 = 往下看）
     * @return 若符合目標方向則回傳 {@code true}
     */
    public boolean matches(double deltaYaw, double deltaPitch) {
        return matches(deltaYaw, deltaPitch, ModConfig.MIN_ANGLE_DEGREES.get());
    }

    /**
     * 判斷給定的視角增量是否「有效符合」此目標方向，並以呼叫端提供的
     * 最小角度門檻（degrees）取代 Config 讀取。
     *
     * <p><b>設計意圖：</b>將 Config 讀取與核心判定分離，讓單元測試
     * 可以完全不觸碰 {@link ModConfig}（亦即不觸發 NeoForge 的
     * {@code ModConfigSpec} 類別初始化），於純 JVM 環境獨立執行。</p>
     *
     * <p><b>執行期請勿直接呼叫此多載</b>，除非你已取得伺服器端權威值；
     * 一般遊戲邏輯請使用 {@link #matches(double, double)}。</p>
     *
     * @param deltaYaw         Yaw 增量（degrees）
     * @param deltaPitch       Pitch 增量（degrees）
     * @param minAngleDegrees  判定為有效甩動的最小向量長度（degrees），應 > 0
     * @return 若符合目標方向則回傳 {@code true}
     */
    public boolean matches(double deltaYaw, double deltaPitch, double minAngleDegrees) {
        if (Math.hypot(deltaYaw, deltaPitch) < minAngleDegrees) {
            return false;
        }
        double angle = deltaToAngle(deltaYaw, deltaPitch);
        double deviation = angleDeviation(angle, this.baseAngleDegrees);
        return deviation <= MAX_DEVIATION_DEGREES;
    }

    /**
     * 判斷給定的視角增量是否「明顯反向」——即方向與此目標方向相差超過 90°。
     *
     * <p>此方法<b>不</b>檢查最小角度門檻；若增量過小，呼叫端應先自行以
     * {@link ModConfig#MIN_ANGLE_DEGREES} 過濾，避免雜訊被誤判為「反向」。</p>
     *
     * @param deltaYaw   本次視角變化的 Yaw 增量（degrees）
     * @param deltaPitch 本次視角變化的 Pitch 增量（degrees）
     * @return 若方向偏離目標超過 90° 則回傳 {@code true}
     */
    public boolean isClearlyWrong(double deltaYaw, double deltaPitch) {
        double angle = deltaToAngle(deltaYaw, deltaPitch);
        double deviation = angleDeviation(angle, this.baseAngleDegrees);
        return deviation > CLEARLY_WRONG_DEVIATION_DEGREES;
    }

    /**
     * 從視角增量推斷「最接近」的 8 方位方向。
     *
     * <p>此方法可用於除錯、或作為未指定目標時的兜底判斷。
     * 若增量為零（或極小），回傳的結果沒有實際意義，呼叫端應自行過濾。</p>
     *
     * @param deltaYaw   Yaw 增量（degrees）
     * @param deltaPitch Pitch 增量（degrees）
     * @return 角距離最小的方向
     */
    public static SwipeDirection closestTo(double deltaYaw, double deltaPitch) {
        double angle = deltaToAngle(deltaYaw, deltaPitch);
        SwipeDirection best = UP;
        double bestDeviation = Double.MAX_VALUE;
        for (SwipeDirection dir : values()) {
            double dev = angleDeviation(angle, dir.baseAngleDegrees);
            if (dev < bestDeviation) {
                bestDeviation = dev;
                best = dir;
            }
        }
        return best;
    }

    // ============================================================
    // 角度工具方法
    // ============================================================

    /**
     * 將視角增量映射到以 UP=0°、順時針遞增的角度系統。
     *
     * <p>推導：令 {@code x = deltaYaw}（右為正）、{@code y = -deltaPitch}（上為正），
     * 則 {@code atan2(y, x)} 給出從 RIGHT 方向逆時針起算的角度。
     * 將結果規範化並轉為「從 UP 順時針起算」即可。</p>
     *
     * @param deltaYaw   Yaw 增量（degrees）
     * @param deltaPitch Pitch 增量（degrees）
     * @return 角度，範圍 [0, 360)
     */
    private static double deltaToAngle(double deltaYaw, double deltaPitch) {
        // atan2(x=deltaYaw, y=-deltaPitch)：UP(0,1) -> 0°；RIGHT(1,0) -> 90°；
        // DOWN(0,-1) -> 180°；LEFT(-1,0) -> 270°（或 -90°，經規範化後為 270°）。
        double angle = Math.toDegrees(Math.atan2(deltaYaw, -deltaPitch));
        return ((angle % 360.0) + 360.0) % 360.0;
    }

    /**
     * 計算兩個角度之間的最小角距離（degrees），考慮環繞。
     *
     * @param a 角度 a（degrees，任意範圍）
     * @param b 角度 b（degrees，任意範圍）
     * @return 最小角距離，範圍 [0, 180]
     */
    private static double angleDeviation(double a, double b) {
        double diff = (a - b) % 360.0;
        if (diff > 180.0) {
            diff -= 360.0;
        } else if (diff < -180.0) {
            diff += 360.0;
        }
        return Math.abs(diff);
    }
}