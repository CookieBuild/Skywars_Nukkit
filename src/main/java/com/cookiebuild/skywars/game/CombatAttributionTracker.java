package com.cookiebuild.skywars.game;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

public final class CombatAttributionTracker {
    private record Hit(UUID attacker, long at) {
    }

    private final long attributionWindowMillis;
    private final LongSupplier clock;
    private final Map<UUID, Hit> hits = new HashMap<>();

    public CombatAttributionTracker(long attributionWindowMillis, LongSupplier clock) {
        if (attributionWindowMillis < 0 || clock == null) {
            throw new IllegalArgumentException("Invalid combat attribution settings");
        }
        this.attributionWindowMillis = attributionWindowMillis;
        this.clock = clock;
    }

    public void record(UUID victim, UUID attacker) {
        if (victim != null && attacker != null && !victim.equals(attacker)) {
            hits.put(victim, new Hit(attacker, clock.getAsLong()));
        }
    }

    public UUID consume(UUID victim) {
        Hit hit = hits.remove(victim);
        return hit != null && clock.getAsLong() - hit.at() <= attributionWindowMillis ? hit.attacker() : null;
    }

    public void remove(UUID player) {
        hits.remove(player);
        hits.entrySet().removeIf(entry -> entry.getValue().attacker().equals(player));
    }

    public void clear() {
        hits.clear();
    }
}
