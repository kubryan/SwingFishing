package com.swingfishing.fishing.game;

// 👇 解決報錯的關鍵：把這三個 Assertions 的靜態方法導入進來 👇
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link SwipeDirection} 的純 JVM 單元測試。
 */
class SwipeDirectionTest {

    private static final double MIN_ANGLE_DEGREES = 15.0;

    @Test
    @DisplayName("純向右轉 (deltaYaw=+20, deltaPitch=0) → RIGHT")
    void pureRightMatchesRight() {
        double deltaYaw = 20.0;
        double deltaPitch = 0.0;

        assertTrue(SwipeDirection.RIGHT.matches(deltaYaw, deltaPitch, MIN_ANGLE_DEGREES),
                "RIGHT 應接受純向右轉");
        assertFalse(SwipeDirection.LEFT.matches(deltaYaw, deltaPitch, MIN_ANGLE_DEGREES));
        assertFalse(SwipeDirection.UP.matches(deltaYaw, deltaPitch, MIN_ANGLE_DEGREES));
        assertFalse(SwipeDirection.DOWN.matches(deltaYaw, deltaPitch, MIN_ANGLE_DEGREES));
    }

    @Test
    @DisplayName("純向左轉 (deltaYaw=-20, deltaPitch=0) → LEFT")
    void pureLeftMatchesLeft() {
        double deltaYaw = -20.0;
        double deltaPitch = 0.0;

        assertTrue(SwipeDirection.LEFT.matches(deltaYaw, deltaPitch, MIN_ANGLE_DEGREES));
        assertFalse(SwipeDirection.RIGHT.matches(deltaYaw, deltaPitch, MIN_ANGLE_DEGREES));
    }

    @Test
    @DisplayName("純往上抬頭 (deltaYaw=0, deltaPitch=-20) → UP")
    void pureUpMatchesUp() {
        double deltaYaw = 0.0;
        double deltaPitch = -20.0;

        assertTrue(SwipeDirection.UP.matches(deltaYaw, deltaPitch, MIN_ANGLE_DEGREES));
        assertFalse(SwipeDirection.DOWN.matches(deltaYaw, deltaPitch, MIN_ANGLE_DEGREES));
    }

    @Test
    @DisplayName("純往下低頭 (deltaYaw=0, deltaPitch=+20) → DOWN")
    void pureDownMatchesDown() {
        double deltaYaw = 0.0;
        double deltaPitch = 20.0;

        assertTrue(SwipeDirection.DOWN.matches(deltaYaw, deltaPitch, MIN_ANGLE_DEGREES));
        assertFalse(SwipeDirection.UP.matches(deltaYaw, deltaPitch, MIN_ANGLE_DEGREES));
    }

    @Test
    @DisplayName("右上角 (deltaYaw=+15, deltaPitch=-15) → UP_RIGHT")
    void upRightMatchesUpRight() {
        double deltaYaw = 15.0;
        double deltaPitch = -15.0;

        assertTrue(SwipeDirection.UP_RIGHT.matches(deltaYaw, deltaPitch, MIN_ANGLE_DEGREES));
        assertFalse(SwipeDirection.UP.matches(deltaYaw, deltaPitch, MIN_ANGLE_DEGREES));
        assertFalse(SwipeDirection.RIGHT.matches(deltaYaw, deltaPitch, MIN_ANGLE_DEGREES));
    }

    @Test
    @DisplayName("甩動幅度太小 (2.0° < 15.0°) → 不算有效甩動")
    void tinySwipeDoesNotMatchAnyDirection() {
        double deltaYaw = 2.0;
        double deltaPitch = 0.0;

        for (SwipeDirection dir : SwipeDirection.values()) {
            assertFalse(dir.matches(deltaYaw, deltaPitch, MIN_ANGLE_DEGREES),
                    "幅度 2.0° 不應匹配 " + dir.name());
        }
    }

    @Nested
    @DisplayName("邊界與輔助方法")
    class BoundariesAndHelpers {

        @Test
        @DisplayName("幅度恰等於門檻 15.0° 且方向正確 → 通過")
        void exactlyAtThresholdMatchesWhenDirectionIsCorrect() {
            assertTrue(SwipeDirection.RIGHT.matches(15.0, 0.0, MIN_ANGLE_DEGREES));
        }

        @Test
        @DisplayName("向右目標但增量純向左 → isClearlyWrong = true")
        void oppositeDirectionIsClearlyWrong() {
            double deltaYaw = -20.0;
            double deltaPitch = 0.0;
            assertTrue(SwipeDirection.RIGHT.isClearlyWrong(deltaYaw, deltaPitch));
            assertFalse(SwipeDirection.LEFT.isClearlyWrong(deltaYaw, deltaPitch));
        }

        @Test
        @DisplayName("closestTo 推斷方向")
        void closestToInfersDirection() {
            assertEquals(SwipeDirection.RIGHT, SwipeDirection.closestTo(20.0, 0.0));
            assertEquals(SwipeDirection.UP, SwipeDirection.closestTo(0.0, -20.0));
            assertEquals(SwipeDirection.UP_RIGHT, SwipeDirection.closestTo(15.0, -15.0));
        }

        @Test
        @DisplayName("角度環繞判定")
        void angleWrapAround() {
            assertTrue(SwipeDirection.RIGHT.matches(1.0, 0.0, 1.0E-3));
            assertTrue(SwipeDirection.LEFT.matches(-1.0, 0.0, 1.0E-3));
        }
    }
}