package com.cookiebuild.skywars.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.cookiebuild.skywars.kit.SkyWarsKit;

class KitMenuSnapshotTest {
    @Test
    void exposesEveryKitToBothJavaAndBedrockMenusInStableOrder() {
        List<KitMenuSnapshot.Entry> entries = KitMenuSnapshot.entries(
                kit -> kit == SkyWarsKit.SCOUT || kit == SkyWarsKit.BUILDER,
                SkyWarsKit.BUILDER);

        assertEquals(List.of(SkyWarsKit.values()), entries.stream().map(KitMenuSnapshot.Entry::kit).toList());
        assertTrue(entries.getFirst().unlocked());
        assertEquals("select", entries.getFirst().action());
        assertTrue(entries.get(2).selected());
        assertFalse(entries.get(1).unlocked());
        assertEquals("purchase", entries.get(1).action());
    }
}
