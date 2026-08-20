package com.cookiebuild.skywars.game;

import java.util.ArrayList;
import java.util.List;

/** Chooses a nearby but safely separated island cluster for low-population matches. */
final class CompactSpawnSelector {
    private static final double MINIMUM_SEPARATION_SQUARED = 20.0 * 20.0;

    record Point(double x, double y, double z) {
        double distanceSquared(Point other) {
            double dx = x - other.x;
            double dy = y - other.y;
            double dz = z - other.z;
            return dx * dx + dy * dy + dz * dz;
        }
    }

    private record Candidate(List<Integer> indices, double maximumDistance, double totalDistance) { }

    private CompactSpawnSelector() {
    }

    static List<Integer> select(List<Point> points, int players, int seed) {
        if (points == null || points.stream().anyMatch(java.util.Objects::isNull)
                || players < 1 || players > points.size()) {
            throw new IllegalArgumentException("A valid spawn point is required for every player");
        }
        if (players > 4) {
            return java.util.stream.IntStream.range(0, players).boxed().toList();
        }
        List<Candidate> separated = candidates(points, players, true);
        List<Candidate> available = separated.isEmpty() ? candidates(points, players, false) : separated;
        Candidate best = available.stream().min(java.util.Comparator
                .comparingDouble(Candidate::maximumDistance)
                .thenComparingDouble(Candidate::totalDistance)).orElseThrow();
        List<Candidate> ties = available.stream()
                .filter(candidate -> Double.compare(candidate.maximumDistance(), best.maximumDistance()) == 0
                        && Double.compare(candidate.totalDistance(), best.totalDistance()) == 0)
                .toList();
        return ties.get(Math.floorMod(seed, ties.size())).indices();
    }

    private static List<Candidate> candidates(List<Point> points, int players, boolean enforceSeparation) {
        List<Candidate> candidates = new ArrayList<>();
        choose(points, players, 0, new ArrayList<>(), enforceSeparation, candidates);
        return candidates;
    }

    private static void choose(List<Point> points, int players, int next, List<Integer> chosen,
            boolean enforceSeparation, List<Candidate> candidates) {
        if (chosen.size() == players) {
            double maximum = 0.0;
            double total = 0.0;
            for (int left = 0; left < chosen.size(); left++) {
                for (int right = left + 1; right < chosen.size(); right++) {
                    double distance = points.get(chosen.get(left)).distanceSquared(points.get(chosen.get(right)));
                    if (enforceSeparation && distance < MINIMUM_SEPARATION_SQUARED) return;
                    maximum = Math.max(maximum, distance);
                    total += distance;
                }
            }
            candidates.add(new Candidate(List.copyOf(chosen), maximum, total));
            return;
        }
        int remaining = players - chosen.size();
        for (int index = next; index <= points.size() - remaining; index++) {
            chosen.add(index);
            choose(points, players, index + 1, chosen, enforceSeparation, candidates);
            chosen.removeLast();
        }
    }
}
