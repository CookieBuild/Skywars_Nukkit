package com.cookiebuild.skywars.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import java.util.ResourceBundle;

import org.junit.jupiter.api.Test;

class WaitingMessagesTest {
    @Test
    void keepsTheSharedEnglishWaitingVocabulary() {
        ResourceBundle bundle = ResourceBundle.getBundle("skywars_messages", Locale.ENGLISH);

        assertEquals("WAITING LOBBY", bundle.getString("skywars.waiting.title"));
        assertTrue(bundle.getString("skywars.waiting.players").startsWith("Waiting for players"));
        assertTrue(bundle.getString("skywars.waiting.starting").startsWith("Starting in {0}s"));
    }
}
