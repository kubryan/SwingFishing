package com.swingfishing.fishing;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 魚 ID（不含命名空間）→ 稀有度。
 * 查無資料時預設 COMMON，避免漏登造成崩潰。
 */
public final class FishRarityLookup {

    private static final Map<String, FishRarity> BY_ID = new HashMap<>();
    private static final Map<FishRarity, Set<String>> BY_RARITY = new EnumMap<>(FishRarity.class);

    static {
        for (FishRarity r : FishRarity.values()) {
            BY_RARITY.put(r, new HashSet<>());
        }

        // ========== COMMON ==========
        put(FishRarity.COMMON,
                "ordinary_carp", "ordinary_catfish", "ordinary_mackerel", "ordinary_shrimp",
                "eel", "muddy_catfish", "sludge_catfish", "rockgill", "sea_bass",
                "pale_carp", "pale_pinfish", "pinfish", "mossfin", "dripfin", "driftfin",
                "drifting_bream", "downfall_bream", "willow_bream", "agave_bream",
                "carpenjoe", "lilac_minnow", "crystalback_minnow", "azure_crystalback_minnow",
                "chorus_minnow", "blue_herring", "deepjaw_herring", "ironjaw_herring",
                "mistback_chub", "frostgill_chub", "sandtail", "stonefish", "fish_bones",
                "tentacle", "starfish", "bluegigi", "charfish", "flowerfish", "shroomfish",
                "sporefish", "cactifish", "mothfish", "starfish_shashlik"
        );

        // ========== UNCOMMON ==========
        put(FishRarity.UNCOMMON,
                "pink_koi", "red_koi", "yellow_koi", "purple_carp", "luminous_carp",
                "sunflower_carp", "sun_seeking_carp", "petaldrift_carp", "mirage_carp",
                "deformed_carp", "tropical_catfish", "ripple_catfish", "sage_catfish",
                "crystalback_trout", "frostjaw_trout", "pyrotrout",
                "blue_ice_pike", "wintery_pike", "lush_pike", "silverfin_pike",
                "scalding_pike", "ghostly_pike", "aquamarine_pike",
                "lightning_bass", "thunder_bass", "petal_bass",
                "cloudfin", "rainfin", "shadowfin", "silverveil_perch",
                "hollowbelly_darter", "vivid_moss", "blue_crystal_fin",
                "luminous_mackerel", "luminous_shrimp", "mangrove_jack",
                "ice_cod", "anglerfish", "jellyfish",
                // Nether 基礎（上調）
                "deepslatefish", "embergill", "scorchfish", "sculkfish",
                // End 基礎（上調）
                "end_starfish_shashlik", "vesani", "ward"
        );

        // ========== RARE ==========
        put(FishRarity.RARE,
                "crystalback_sturgeon", "icetooth_sturgeon", "oasis_sturgeon", "sunny_sturgeon",
                "charged_thunderfin", "thunderfin", "glimmergill",
                "glowstone_pufferfish", "glowstone_seeker", "fossilized_angelfish", "glowing_dark",
                "amethystback", "bright_amethyst_snapper", "dark_amethyst_snapper",
                "dusktail_snapper", "lily_snapper", "bigeye_tuna", "redscaled_tuna",
                "boreal", "crystalback_boreal", "cryospine", "garnet_mackerel",
                "gold_fan", "rose_siamese_fish", "pandafish", "peakdweller",
                "blossomfish", "livid_bamboo", "yellowstone_fish"
        );

        // ========== EPIC ==========
        put(FishRarity.EPIC,
                "divine_catfish", "divine_jellyfish",
                "magma_fish", "sculk_ward",
                "aurora", "elderscale", "end_glow", "end_jellyfish", "end_starfish",
                "voidfin", "cerberay", "dreamliner", "morganite", "obidontiee",
                "whiteveil", "willish", "witchfish", "solarfish"
        );

        // ========== MYTHIC ==========
        put(FishRarity.MYTHIC,
                "voidbiter", "joel" // joel 若你想當 Unique 可再改
                // 其餘從 Epic 上調或後補
        );

        // ========== LEGENDARY ==========
        put(FishRarity.LEGENDARY,
                "elderscale", "voidbiter", "divine_jellyfish", "magma_fish", "aurora"
                // 注意：若上面已 put 過會被覆蓋，最終以最後一次為準
        );

        // ========== UNIQUE ==========
        put(FishRarity.UNIQUE,
                "joel", "the_quarrish", "suneater"
        );
    }

    private static void put(FishRarity rarity, String... ids) {
        for (String id : ids) {
            BY_ID.put(id, rarity);
            BY_RARITY.get(rarity).add(id);
        }
    }

    public static FishRarity get(String fishId) {
        return BY_ID.getOrDefault(fishId, FishRarity.COMMON);
    }

    /** 取得某稀有度下、且屬於指定 ID 集合的魚（用於生態域過濾） */
    public static Set<String> getFishOfRarityInPool(FishRarity rarity, Set<String> biomePool) {
        Set<String> result = new HashSet<>();
        for (String id : BY_RARITY.getOrDefault(rarity, Collections.emptySet())) {
            if (biomePool.contains(id)) {
                result.add(id);
            }
        }
        return result;
    }

    private FishRarityLookup() {}
}