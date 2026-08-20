package com.cookiebuild.skywars.game;

/** Pure delivery and relative-direction policy for full-inventory tracker fallback. */
public final class TrackerDelivery {
    public enum Mode { EXISTING_ITEM, GIVE_ITEM, GUIDANCE }
    public enum Direction { AHEAD, RIGHT, BEHIND, LEFT }

    private TrackerDelivery() {
    }

    public static Mode mode(boolean hasCompass, int firstEmptySlot) {
        if (hasCompass) return Mode.EXISTING_ITEM;
        return firstEmptySlot >= 0 ? Mode.GIVE_ITEM : Mode.GUIDANCE;
    }

    public static Direction relativeDirection(float playerYaw, double deltaX, double deltaZ) {
        double targetYaw = Math.toDegrees(Math.atan2(-deltaX, deltaZ));
        double relative = normalize(targetYaw - playerYaw);
        if (relative >= -45.0 && relative < 45.0) return Direction.AHEAD;
        if (relative >= 45.0 && relative < 135.0) return Direction.RIGHT;
        if (relative >= -135.0 && relative < -45.0) return Direction.LEFT;
        return Direction.BEHIND;
    }

    private static double normalize(double degrees) {
        double normalized = degrees % 360.0;
        if (normalized >= 180.0) normalized -= 360.0;
        if (normalized < -180.0) normalized += 360.0;
        return normalized;
    }
}
