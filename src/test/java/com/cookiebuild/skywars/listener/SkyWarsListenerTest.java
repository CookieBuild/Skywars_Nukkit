package com.cookiebuild.skywars.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerInteractEvent;
import org.junit.jupiter.api.Test;

class SkyWarsListenerTest {
    @Test
    void signedKitSelectorStillRunsWhenAnotherPluginCancelledInteraction() throws Exception {
        EventHandler handler = SkyWarsListener.class
                .getMethod("onKitSelector", PlayerInteractEvent.class)
                .getAnnotation(EventHandler.class);

        assertEquals(EventPriority.HIGHEST, handler.priority());
        assertFalse(handler.ignoreCancelled());
    }
}
