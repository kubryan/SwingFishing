package com.swingfishing.fishing.data;

import com.swingfishing.fishing.game.SwipeDirection;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FishingMiniGameDataTest {

    @Test
    @DisplayName("初始狀態應為未啟動且數值為零")
    void initialStateIsInactive() {
        FishingMiniGameData data = new FishingMiniGameData();
        assertFalse(data.isActive());
        assertNull(data.getTargetDirection());
        assertEquals(0, data.getSuccessCount());
        assertEquals(0, data.getFailedCount());
        assertEquals(0, data.getTicksElapsed());
        assertNull(data.getBobberUUID());
    }

    @Test
    @DisplayName("start() 應正確初始化狀態")
    void startInitializesState() {
        FishingMiniGameData data = new FishingMiniGameData();
        UUID bobberId = UUID.randomUUID();

        data.start(SwipeDirection.UP, bobberId, 0);

        assertTrue(data.isActive());
        assertEquals(SwipeDirection.UP, data.getTargetDirection());
        assertEquals(0, data.getSuccessCount());
        assertEquals(0, data.getFailedCount());
        assertEquals(0, data.getTicksElapsed());
        assertEquals(bobberId, data.getBobberUUID());
    }

    @Test
    @DisplayName("成功與失誤次數增加，並重置計時器")
    void incrementCountsAndResetTimer() {
        FishingMiniGameData data = new FishingMiniGameData();
        data.start(SwipeDirection.UP, UUID.randomUUID(), 0);

        data.tickTimer();
        data.tickTimer();
        assertEquals(2, data.getTicksElapsed());

        // 成功後計時器歸零，並更新目標方向
        data.incrementSuccess(SwipeDirection.RIGHT);
        assertEquals(1, data.getSuccessCount());
        assertEquals(0, data.getTicksElapsed());
        assertEquals(SwipeDirection.RIGHT, data.getTargetDirection());

        data.tickTimer();
        assertEquals(1, data.getTicksElapsed());

        // 失誤後計時器歸零，目標方向不變
        data.incrementFailed();
        assertEquals(1, data.getFailedCount());
        assertEquals(0, data.getTicksElapsed());
        assertEquals(SwipeDirection.RIGHT, data.getTargetDirection());
    }

    @Test
    @DisplayName("達到成功或失誤上限時 isFinished 應回傳 true")
    void isFinishedChecksThresholds() {
        FishingMiniGameData data = new FishingMiniGameData();
        data.start(SwipeDirection.UP, UUID.randomUUID(), 0);

        int requiredSuccess = 5;
        int maxFailed = 3;

        assertFalse(data.isFinished(requiredSuccess, maxFailed));

        // 模擬 2 次成功，2 次失誤（未達標）
        data.incrementSuccess(SwipeDirection.UP);
        data.incrementSuccess(SwipeDirection.UP);
        data.incrementFailed();
        data.incrementFailed();
        assertFalse(data.isFinished(requiredSuccess, maxFailed));

        // 第 3 次失誤（達標）
        data.incrementFailed();
        assertTrue(data.isFinished(requiredSuccess, maxFailed));
    }

    @Test
    @DisplayName("clear() 應徹底重置所有狀態")
    void clearResetsEverything() {
        FishingMiniGameData data = new FishingMiniGameData();
        data.start(SwipeDirection.UP, UUID.randomUUID(), 10);
        data.incrementSuccess(SwipeDirection.RIGHT);
        data.incrementFailed();

        data.clear();

        assertFalse(data.isActive());
        assertNull(data.getTargetDirection());
        assertEquals(0, data.getSuccessCount());
        assertEquals(0, data.getFailedCount());
        assertEquals(0, data.getTicksElapsed());
        assertNull(data.getBobberUUID());
    }

    @Test
    void caughtFishSurvivesNbtRoundTrip() {
        FishingMiniGameData data = new FishingMiniGameData();
        assertTrue(data.addCaughtFish("agave_bream"));
        assertTrue(data.addCaughtFish("eel"));
        assertFalse(data.addCaughtFish("eel")); // 重複不算新發現

        CompoundTag tag = data.serializeNBT(/* provider 可傳 null，目前實作未使用 */ null);

        FishingMiniGameData restored = new FishingMiniGameData();
        restored.deserializeNBT(null, tag);

        assertEquals(2, restored.getCaughtFish().size());
        assertTrue(restored.hasCaughtFish("agave_bream"));
        assertTrue(restored.hasCaughtFish("eel"));
        assertFalse(restored.hasCaughtFish("swordfish"));
    }

    @Test
    void clearDoesNotWipeCaughtFish() {
        FishingMiniGameData data = new FishingMiniGameData();
        data.addCaughtFish("eel");
        data.start(SwipeDirection.UP, UUID.randomUUID(), 0);
        data.incrementFailed();

        data.clear(); // 模擬脫鉤/死亡/登出

        assertFalse(data.isActive());          // 單次狀態被清
        assertEquals(0, data.getFailedCount());
        assertTrue(data.hasCaughtFish("eel")); // 跨局資料保留
    }
}