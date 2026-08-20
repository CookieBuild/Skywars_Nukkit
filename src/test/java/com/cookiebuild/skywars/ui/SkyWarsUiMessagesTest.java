package com.cookiebuild.skywars.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;

import org.junit.jupiter.api.Test;

class SkyWarsUiMessagesTest {
    @Test
    void javaAndBedrockFlowsAreCompleteInPlayerFacingLocales() {
        List<String> keys = List.of(
                "skywars.kit.catalog", "skywars.kit.progression", "skywars.kit.action.select",
                "skywars.kit.action.purchase", "skywars.kit.confirm.title", "skywars.kit.confirm.buy",
                "skywars.kit.purchase_select", "skywars.ui.back", "skywars.refill",
                "skywars.tracker.enabled", "skywars.tracker.guidance_enabled", "skywars.tracker.guidance",
                "skywars.tracker.direction.ahead", "skywars.build_limit", "skywars.scoreboard.alive",
                "skywars.scoreboard.objective", "skywars.objective.open_chest",
                "skywars.objective.reach_center", "skywars.objective.survive",
                "skywars.outcome.eliminated");
        for (Locale locale : List.of(Locale.ENGLISH, Locale.FRENCH, new Locale("es"),
                Locale.forLanguageTag("pt-BR"))) {
            ResourceBundle bundle = ResourceBundle.getBundle("skywars_messages", locale);
            for (String key : keys) {
                assertTrue(bundle.containsKey(key) && !bundle.getString(key).isBlank(), locale + " " + key);
            }
        }
    }

    @Test
    void everyShippedSkyWarsBundleHasTheSameKeys() {
        Set<String> english = ResourceBundle.getBundle("skywars_messages", Locale.ENGLISH).keySet();
        for (Locale locale : List.of(Locale.FRENCH, Locale.GERMAN, new Locale("es"),
                new Locale("pa"), Locale.forLanguageTag("pt-BR"))) {
            assertTrue(english.equals(ResourceBundle.getBundle("skywars_messages", locale).keySet()),
                    () -> "SkyWars locale keys differ for " + locale);
        }
    }
}
