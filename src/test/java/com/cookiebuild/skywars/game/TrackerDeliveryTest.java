package com.cookiebuild.skywars.game;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TrackerDeliveryTest {
    @Test
    void thirtySixOccupiedSlotsUseGuidanceInsteadOfClaimingACompassWasGiven() {
        int firstEmptySlotForFullInventory = -1;
        assertEquals(TrackerDelivery.Mode.GUIDANCE,
                TrackerDelivery.mode(false, firstEmptySlotForFullInventory));
        assertEquals(TrackerDelivery.Mode.EXISTING_ITEM,
                TrackerDelivery.mode(true, firstEmptySlotForFullInventory));
    }

    @Test
    void relativeDirectionsRemainStable() {
        assertEquals(TrackerDelivery.Direction.AHEAD,
                TrackerDelivery.relativeDirection(0.0f, 0.0, 10.0));
        assertEquals(TrackerDelivery.Direction.RIGHT,
                TrackerDelivery.relativeDirection(0.0f, -10.0, 0.0));
        assertEquals(TrackerDelivery.Direction.LEFT,
                TrackerDelivery.relativeDirection(0.0f, 10.0, 0.0));
        assertEquals(TrackerDelivery.Direction.BEHIND,
                TrackerDelivery.relativeDirection(0.0f, 0.0, -10.0));
    }
}
