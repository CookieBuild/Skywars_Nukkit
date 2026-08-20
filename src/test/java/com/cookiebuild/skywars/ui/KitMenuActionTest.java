package com.cookiebuild.skywars.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class KitMenuActionTest {
    @Test
    void distinguishesSelectionPurchaseConfirmationAndBack() {
        assertEquals(KitMenuAction.SELECT, KitMenuAction.from("select"));
        assertEquals(KitMenuAction.PURCHASE, KitMenuAction.from("PURCHASE"));
        assertEquals(KitMenuAction.CONFIRM_PURCHASE, KitMenuAction.from("confirm_purchase"));
        assertEquals(KitMenuAction.BACK, KitMenuAction.from("back"));
        assertNull(KitMenuAction.from("take_item"));
        assertNull(KitMenuAction.from(null));
    }
}
