package com.cookiebuild.skywars.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class MapConfigurationTest {
    private static final Pattern SPAWN = Pattern.compile("(?m)^      - \\[([^]]+)]$");

    @Test
    void declaresEveryRecoveredArchiveWithItsHistoricalMapIdentity() throws IOException {
        String config = config();

        assertEquals("legacy-0.zip", value(config, "legacy-0", "archive"));
        assertEquals("legacy-1.zip", value(config, "legacy-1", "archive"));
        assertEquals("legacy-2.zip", value(config, "legacy-2", "archive"));
        assertEquals("legacy-4.zip", value(config, "legacy-4", "archive"));
        assertEquals(4, count(config, "    archive: "));
    }

    @Test
    void preservesTheElevenUniqueLegacyZeroIslandSpawns() throws IOException {
        List<String> spawns = spawns(section(config(), "legacy-0"));

        assertEquals(11, spawns.size());
        assertEquals(11, new HashSet<>(spawns).size());
        assertEquals("51.5, 53.5, 640.5, -79.0", spawns.getFirst());
        assertEquals("57.5, 53.5, 623.5, -59.0", spawns.getLast());
    }

    @Test
    void keepsRecoveredSpawnCorrectionsThatAvoidBlockIntersections() throws IOException {
        String config = config();

        assertEquals("-1589.5, 48.5, -870.5, -1.0", spawns(section(config, "legacy-1")).getFirst());
        assertEquals("[-475.5, 60.0, 367.5, 0.0]", value(config, "legacy-2", "spectator-spawn"));
        assertTrue(section(config, "legacy-2").contains("kill-y: 20.0"));
    }

    private static String config() throws IOException {
        try (InputStream input = MapConfigurationTest.class.getResourceAsStream("/config.yml")) {
            if (input == null) {
                throw new IOException("config.yml is missing from test resources");
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String section(String config, String mapName) {
        String marker = "  " + mapName + ":\n";
        int start = config.indexOf(marker);
        if (start < 0) {
            throw new IllegalArgumentException("Missing map section " + mapName);
        }
        int end = config.indexOf("\n  legacy-", start + marker.length());
        return config.substring(start, end < 0 ? config.indexOf("\nrewards:", start) : end);
    }

    private static String value(String config, String mapName, String key) {
        Matcher matcher = Pattern.compile("(?m)^    " + Pattern.quote(key) + ": (.+)$")
                .matcher(section(config, mapName));
        if (!matcher.find()) {
            throw new IllegalArgumentException("Missing " + mapName + "." + key);
        }
        return matcher.group(1);
    }

    private static List<String> spawns(String section) {
        Matcher matcher = SPAWN.matcher(section);
        List<String> result = new ArrayList<>();
        while (matcher.find()) {
            result.add(matcher.group(1));
        }
        return result;
    }

    private static int count(String text, String needle) {
        return (text.length() - text.replace(needle, "").length()) / needle.length();
    }
}
