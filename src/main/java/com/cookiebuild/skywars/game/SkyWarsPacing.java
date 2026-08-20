package com.cookiebuild.skywars.game;

/** Validated pacing values read once per arena. */
public record SkyWarsPacing(int maxSeconds, int borderStartSeconds, int refillSeconds,
        int trackerSeconds, int buildCeilingOffset) {
    public SkyWarsPacing {
        if (maxSeconds < 300 || maxSeconds > 420
                || borderStartSeconds < 60 || borderStartSeconds >= maxSeconds
                || refillSeconds <= borderStartSeconds || refillSeconds >= maxSeconds
                || trackerSeconds < refillSeconds || trackerSeconds >= maxSeconds
                || buildCeilingOffset < 12 || buildCeilingOffset > 40) {
            throw new IllegalArgumentException("SkyWars pacing must remain within the competitive 5-7 minute contract");
        }
    }
}
