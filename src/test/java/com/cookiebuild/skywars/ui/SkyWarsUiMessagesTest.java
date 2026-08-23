package com.cookiebuild.skywars.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class SkyWarsUiMessagesTest {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\d+}");

    @Test
    void javaAndBedrockFlowsAreCompleteInPlayerFacingLocales() {
        List<String> keys = List.of(
                "skywars.kit.catalog", "skywars.kit.progression", "skywars.kit.action.select",
                "skywars.kit.action.purchase", "skywars.kit.confirm.title", "skywars.kit.confirm.buy",
                "skywars.kit.purchase_select", "skywars.ui.back", "skywars.refill",
                "skywars.kit.scout.name", "skywars.kit.armorer.name",
                "skywars.kit.builder.name", "skywars.kit.healer.name",
                "skywars.tracker.enabled", "skywars.tracker.guidance_enabled", "skywars.tracker.guidance",
                "skywars.tracker.direction.ahead", "skywars.build_limit", "skywars.scoreboard.alive",
                "skywars.scoreboard.objective", "skywars.objective.open_chest",
                "skywars.objective.reach_center", "skywars.objective.survive",
                "skywars.outcome.eliminated");
        for (Locale locale : List.of(Locale.ENGLISH, Locale.FRENCH, Locale.of("es"),
                Locale.forLanguageTag("pt-BR"), Locale.of("bg"), Locale.of("hi"))) {
            ResourceBundle bundle = ResourceBundle.getBundle("skywars_messages", locale);
            for (String key : keys) {
                assertTrue(bundle.containsKey(key) && !bundle.getString(key).isBlank(), locale + " " + key);
            }
        }
    }

    @Test
    void everyShippedSkyWarsBundleHasTheSameKeys() {
        ResourceBundle english = ResourceBundle.getBundle("skywars_messages", Locale.ENGLISH);
        for (Locale locale : List.of(Locale.FRENCH, Locale.GERMAN, Locale.of("es"),
                Locale.of("pa"), Locale.forLanguageTag("pt-BR"), Locale.of("bg"), Locale.of("hi"))) {
            ResourceBundle translated = ResourceBundle.getBundle("skywars_messages", locale);
            assertEquals(english.keySet(), translated.keySet(), "SkyWars keys for " + locale);
            for (String key : english.keySet()) {
                assertEquals(placeholders(english.getString(key)), placeholders(translated.getString(key)),
                        key + " placeholders for " + locale);
            }
        }
    }

    private static Set<String> placeholders(String value) {
        Set<String> result = new LinkedHashSet<>();
        Matcher matcher = PLACEHOLDER.matcher(value);
        while (matcher.find()) result.add(matcher.group());
        return result;
    }
}
