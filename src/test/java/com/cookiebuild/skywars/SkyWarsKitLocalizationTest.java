package com.cookiebuild.skywars;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import com.cookiebuild.skywars.kit.SkyWarsKit;

class SkyWarsKitLocalizationTest {
    @Test
    void kitNamesAreLocalizedWhileStableKeysRemainUnchanged() {
        assertEquals("scout", SkyWarsKit.SCOUT.key());
        assertEquals("Éclaireur", SkyWars.kitNameForLocale(Locale.FRENCH, SkyWarsKit.SCOUT));
        assertEquals("Разузнавач", SkyWars.kitNameForLocale(Locale.of("bg"), SkyWarsKit.SCOUT));
        assertEquals("उपचारक", SkyWars.kitNameForLocale(Locale.of("hi"), SkyWarsKit.HEALER));
        assertEquals("Batedor", SkyWars.kitNameForLocale(Locale.of("pt", "PT"), SkyWarsKit.SCOUT));
        assertEquals("Scout", SkyWars.kitNameForLocale(Locale.JAPANESE, SkyWarsKit.SCOUT));
    }

    @Test
    void javaAndBedrockRenderersDoNotUseInternalEnglishDisplayNames() throws IOException {
        for (String relative : List.of(
                "src/main/java/com/cookiebuild/skywars/game/SkyWarsGame.java",
                "src/main/java/com/cookiebuild/skywars/kit/KitCommand.java",
                "src/main/java/com/cookiebuild/skywars/ui/SkyWarsKitSelectionUI.java",
                "src/main/java/com/cookiebuild/skywars/ui/bedrock/BedrockKitSelectionUI.java")) {
            Path path = Path.of(relative);
            if (!Files.exists(path)) path = Path.of("SkyWars").resolve(path);
            String source = Files.readString(path);
            assertFalse(source.contains(".displayName()"), relative);
        }
    }

    @Test
    void missingTranslationsNeverExposeTechnicalKeys() {
        assertEquals("Text unavailable", SkyWars.messageForLocale(
                Locale.GERMAN, "skywars.unknown.technical.key"));
    }
}
