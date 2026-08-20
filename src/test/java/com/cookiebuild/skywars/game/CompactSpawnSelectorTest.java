package com.cookiebuild.skywars.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class CompactSpawnSelectorTest {
    @Test
    void twoToFourPlayersUseANearbyIslandClusterInsteadOfTheWholeMap() {
        List<CompactSpawnSelector.Point> ring = IntStream.range(0, 12)
                .mapToObj(index -> {
                    double angle = index * Math.PI * 2.0 / 12.0;
                    return new CompactSpawnSelector.Point(Math.cos(angle) * 60.0, 64.0, Math.sin(angle) * 60.0);
                }).toList();

        for (int players = 2; players <= 4; players++) {
            List<Integer> selected = CompactSpawnSelector.select(ring, players, 7);
            assertEquals(players, selected.size());
            double maximum = selected.stream().flatMapToDouble(left -> selected.stream()
                    .filter(right -> right > left)
                    .mapToDouble(right -> ring.get(left).distanceSquared(ring.get(right))))
                    .max().orElse(0.0);
            assertTrue(maximum <= 7_201.0, "low-population islands should stay within one compact arc");
        }
    }

    @Test
    void avoidsSpawnIslandsThatAreTooCloseWhenASafePairExists() {
        List<CompactSpawnSelector.Point> points = List.of(
                new CompactSpawnSelector.Point(0, 64, 0),
                new CompactSpawnSelector.Point(8, 64, 0),
                new CompactSpawnSelector.Point(30, 64, 0));

        List<Integer> selected = CompactSpawnSelector.select(points, 2, 0);
        assertEquals(List.of(1, 2), selected);
    }

    @Test
    void largerMatchesKeepTheAuthoredSpawnOrder() {
        List<CompactSpawnSelector.Point> points = IntStream.range(0, 8)
                .mapToObj(index -> new CompactSpawnSelector.Point(index * 10.0, 64.0, 0.0)).toList();
        assertEquals(List.of(0, 1, 2, 3, 4), CompactSpawnSelector.select(points, 5, 42));
    }
}
