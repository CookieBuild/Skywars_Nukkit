package com.cookiebuild.skywars.game;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Pure deterministic timeout ranking: contribution first, UUID only as a final stable tie-break. */
record TimeoutStanding(UUID playerId, int kills, double health, int middleChests, int chestsOpened) {
    private static final Comparator<TimeoutStanding> COMPETITIVE_RANKING = Comparator
            .comparingInt(TimeoutStanding::kills).reversed()
            .thenComparing(Comparator.comparingDouble(TimeoutStanding::health).reversed())
            .thenComparing(Comparator.comparingInt(TimeoutStanding::middleChests).reversed())
            .thenComparing(Comparator.comparingInt(TimeoutStanding::chestsOpened).reversed());

    static UUID winner(List<TimeoutStanding> standings) {
        List<TimeoutStanding> ranked = standings.stream()
                .sorted(COMPETITIVE_RANKING.thenComparing(standing -> standing.playerId().toString()))
                .toList();
        if (ranked.isEmpty()) return null;
        if (ranked.size() > 1 && COMPETITIVE_RANKING.compare(ranked.get(0), ranked.get(1)) == 0) {
            return null;
        }
        return ranked.getFirst().playerId();
    }
}
