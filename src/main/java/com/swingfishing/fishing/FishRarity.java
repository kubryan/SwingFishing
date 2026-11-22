package com.swingfishing.fishing;

/**
 * 七階魚類稀有度（含對應小遊戲難度）。
 * 上鉤時先 roll 稀有度，再從該階 + 生態域魚池抽魚，並套用本階難度。
 */
public enum FishRarity {

    COMMON   (3, 4, 32, 320, "common"),      // ~32%
    UNCOMMON (4, 3, 28, 240, "uncommon"),    // ~24%
    RARE     (5, 3, 24, 180, "rare"),        // ~18%
    EPIC     (6, 2, 22, 120, "epic"),        // ~12%
    MYTHIC   (7, 2, 20,  70, "mythic"),      // ~7%
    LEGENDARY(8, 1, 18,  45, "legendary"),   // ~4.5%
    UNIQUE   (9, 1, 16,  25, "unique");      // ~2.5%

    /** 完成所需成功甩動次數 */
    public final int requiredSuccess;
    /** 允許失誤上限 */
    public final int maxFailed;
    /** 單一方向作答時限（ticks） */
    public final int timeoutTicks;
    /** 稀有度權重（相對值，總和約 1000） */
    public final int weight;
    private final String path;

    FishRarity(int requiredSuccess, int maxFailed, int timeoutTicks, int weight, String path) {
        this.requiredSuccess = requiredSuccess;
        this.maxFailed = maxFailed;
        this.timeoutTicks = timeoutTicks;
        this.weight = weight;
        this.path = path;
    }

    public String getTranslationKey() {
        return "rarity.swingfishing." + path;
    }

    /** 依權重隨機抽一階稀有度 */
    public static FishRarity roll() {
        int total = 0;
        for (FishRarity r : values()) total += r.weight;
        int roll = (int) (Math.random() * total);
        int acc = 0;
        for (FishRarity r : values()) {
            acc += r.weight;
            if (roll < acc) return r;
        }
        return COMMON;
    }
}