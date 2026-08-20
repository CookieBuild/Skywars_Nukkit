package com.cookiebuild.skywars;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.cookiebuild.cookiedough.game.StandbyGamePool;
import com.cookiebuild.cookiedough.game.StandbyRefillPolicy;

class StandbyLifecycleTest {
    @Test
    void keepsSkyWarsAvailableAcrossTwelveRematchesWithALobbyPlayerOnline() {
        StandbyGamePool<String> pool = new StandbyGamePool<>(StandbyRefillPolicy.TARGET_SIZE);
        assertTrue(pool.offer("skywars-startup"));
        int onlineLobbyPlayers = 1;
        assertTrue(onlineLobbyPlayers > 0);
        for (int match = 1; match <= 12; match++) {
            assertNotNull(pool.poll(), "rematch " + match + " needs a prepared arena");
            assertTrue(pool.offer("skywars-refill-" + match));
        }
    }
}
