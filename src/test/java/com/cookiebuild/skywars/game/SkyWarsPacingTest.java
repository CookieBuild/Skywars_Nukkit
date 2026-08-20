package com.cookiebuild.skywars.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SkyWarsPacingTest {
    @Test
    void acceptsTheSixMinuteCompetitiveTimeline() {
        SkyWarsPacing pacing = new SkyWarsPacing(360, 90, 150, 180, 24);

        assertEquals(270, pacing.maxSeconds() - pacing.borderStartSeconds());
    }

    @Test
    void rejectsOldTwelveMinuteAndInvalidEventOrdering() {
        assertThrows(IllegalArgumentException.class, () -> new SkyWarsPacing(720, 360, 500, 600, 24));
        assertThrows(IllegalArgumentException.class, () -> new SkyWarsPacing(360, 180, 120, 200, 24));
    }
}
