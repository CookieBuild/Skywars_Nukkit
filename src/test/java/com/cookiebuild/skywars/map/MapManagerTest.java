package com.cookiebuild.skywars.map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MapManagerTest {
    @TempDir
    Path directory;

    @Test
    void legacyProductionConfigsStillReceivePublicMapNames() {
        assertEquals("Cookie Ring", MapManager.defaultDisplayName("legacy-0"));
        assertEquals("Golden Orbit", MapManager.defaultDisplayName("legacy-4"));
        assertEquals("community-map", MapManager.defaultDisplayName("community-map"));
    }

    @Test
    void rejectsNestedWorldRootsThatWouldGenerateAnEmptyArena() throws IOException {
        Path nested = Files.createDirectories(directory.resolve("world/region"));
        Files.createFile(directory.resolve("world/level.dat"));
        Files.createFile(nested.resolve("r.0.0.mca"));

        assertThrows(IOException.class, () -> MapManager.validateExtractedWorld(directory));
    }

    @Test
    void requiresRegionDataAtTheArchiveRoot() throws IOException {
        Files.createFile(directory.resolve("level.dat"));

        assertThrows(IOException.class, () -> MapManager.validateExtractedWorld(directory));
    }

    @Test
    void acceptsAJavaWorldWithRootMetadataAndRegionData() throws IOException {
        Files.createFile(directory.resolve("level.dat"));
        Path region = Files.createDirectories(directory.resolve("region"));
        Files.createFile(region.resolve("r.0.0.mca"));

        assertDoesNotThrow(() -> MapManager.validateExtractedWorld(directory));
    }
}
