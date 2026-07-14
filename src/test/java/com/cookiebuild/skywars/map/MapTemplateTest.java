package com.cookiebuild.skywars.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

class MapTemplateTest {
    @Test
    void validatesCapacityAndArchivePath() {
        MapTemplate template = new MapTemplate(
                "legacy", "legacy.zip",
                List.of(List.of(0.5, 50.5, 0.5, 0.0), List.of(10.5, 50.5, 0.5, 90.0)),
                List.of(5.5, 70.0, 0.5, 0.0),
                List.of(5.5, 65.0, 0.5, 0.0),
                30.0, 18.0);

        assertEquals(2, template.getCapacity());
        assertThrows(IllegalArgumentException.class, () -> new MapTemplate(
                "legacy", "../outside.zip",
                List.of(List.of(0.0, 1.0, 2.0, 3.0), List.of(4.0, 5.0, 6.0, 7.0)),
                List.of(0.0, 1.0, 2.0, 3.0), List.of(0.0, 1.0, 2.0, 3.0), 0.0, 1.0));
    }

    @Test
    void rejectsMalformedCoordinateLists() {
        assertThrows(IllegalArgumentException.class, () -> new MapTemplate(
                "legacy", "legacy.zip",
                List.of(List.of(0.0, 1.0, 2.0), List.of(4.0, 5.0, 6.0, 7.0)),
                List.of(0.0, 1.0, 2.0, 3.0), List.of(0.0, 1.0, 2.0, 3.0), 0.0, 1.0));
    }
}
