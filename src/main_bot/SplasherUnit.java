package main_bot;

import battlecode.common.*;

public class SplasherUnit extends BaseRobot {

    private enum State { SPLASH_ATTACK, SEEK_DENSITY, RESOURCE_PATTERN }
    private State state = State.SEEK_DENSITY;

    private static final int MIN_SPLASH_VALUE = 3;

    public SplasherUnit(RobotController rc) {
        super(rc);
    }

    @Override
    protected void tick() throws GameActionException {
        updateTowerMemory();
        decideState();
        executeState();
    }

    private void decideState() throws GameActionException {
        if (rc.getPaint() <= 0) {
            state = State.SEEK_DENSITY;
            return;
        }

        if (canCompleteResourcePattern()) {
            state = State.RESOURCE_PATTERN;
            return;
        }

        if (findBestSplashTarget() != null) {
            state = State.SPLASH_ATTACK;
            return;
        }

        state = State.SEEK_DENSITY;
    }

    private void executeState() throws GameActionException {
        switch (state) {
            case SPLASH_ATTACK:    doSplashAttack();            break;
            case SEEK_DENSITY:     doSeekDensity();             break;
            case RESOURCE_PATTERN: doCompleteResourcePattern(); break;
        }
    }

    private void doSplashAttack() throws GameActionException {
        if (rc.isActionReady()) {
            MapLocation best = findBestSplashTarget();
            if (best != null) {
                rc.attack(best);
            }
        }

        if (rc.isMovementReady()) {
            moveToHighDensity();
        }
    }
    
    private MapLocation findBestSplashTarget() throws GameActionException {
        MapInfo[] candidates = rc.senseNearbyMapInfos(rc.getType().actionRadiusSquared);
        MapLocation bestTarget = null;
        int bestScore = MIN_SPLASH_VALUE;

        for (MapInfo candidate : candidates) {
            MapLocation targetLoc = candidate.getMapLocation();
            if (!rc.canAttack(targetLoc)) continue;
            if (candidate.isWall() || candidate.hasRuin()) continue;

            int score = calculateSplashScore(targetLoc);
            if (score > bestScore) {
                bestScore = score;
                bestTarget = targetLoc;
            }
        }

        return bestTarget;
    }

    private int calculateSplashScore(MapLocation loc) throws GameActionException {
        int score = 0;

        MapInfo[] splashArea = rc.senseNearbyMapInfos(loc, 4);
        for (MapInfo t : splashArea) {
            if (t.isWall() || t.hasRuin()) continue;
            if (t.getPaint().isEnemy()) {
                score += 3;
            } else if (!t.getPaint().isAlly()) {
                score += 1;
            }
        }
        return score;
    }

    private void doSeekDensity() throws GameActionException {
        if (rc.getPaint() <= 0) {
            moveAwayFromHome();
        } else {
            moveToHighDensity();
        }
    }

    private void moveToHighDensity() throws GameActionException {
        if (!rc.isMovementReady()) return;

        if (exploreTarget == null || rc.getLocation().distanceSquaredTo(exploreTarget) < 9) {
            exploreTarget = findDensityTarget();
        }

        if (exploreTarget != null) {
            moveTo(exploreTarget);
        } else {
            roamExplore(rc);
        }
    }
    
    private MapLocation findDensityTarget() throws GameActionException {
        MapInfo[] allVisible = rc.senseNearbyMapInfos();
        MapLocation me = rc.getLocation();

        int[] quadrantScore = new int[4];
        int[] quadrantCount = new int[4];

        for (MapInfo tile : allVisible) {
            MapLocation loc = tile.getMapLocation();
            if (tile.isWall() || tile.hasRuin()) continue;

            int dx = loc.x - me.x;
            int dy = loc.y - me.y;

            int q;
            if (dx >= 0 && dy >= 0) q = 0;
            else if (dx < 0 && dy >= 0) q = 1;
            else if (dx < 0 && dy < 0) q = 2;
            else q = 3;

            quadrantCount[q]++;
            if (!tile.getPaint().isAlly()) {
                quadrantScore[q] += tile.getPaint().isEnemy() ? 3 : 1;
            }
        }

        int bestQ = 0;
        double bestDensity = -1;
        for (int i = 0; i < 4; i++) {
            if (quadrantCount[i] == 0) continue;
            double density = (double) quadrantScore[i] / quadrantCount[i];
            if (density > bestDensity) {
                bestDensity = density;
                bestQ = i;
            }
        }

        int targetX = me.x, targetY = me.y;
        int offset = 8;
        switch (bestQ) {
            case 0: targetX += offset; targetY += offset; break;
            case 1: targetX -= offset; targetY += offset; break;
            case 2: targetX -= offset; targetY -= offset; break;
            case 3: targetX += offset; targetY -= offset; break;
        }

        targetX = Math.max(0, Math.min(rc.getMapWidth() - 1, targetX));
        targetY = Math.max(0, Math.min(rc.getMapHeight() - 1, targetY));

        if (bestDensity > 0.3) {
            return new MapLocation(targetX, targetY);
        }

        return pickExploreTarget(rc);
    }

    private boolean canCompleteResourcePattern() throws GameActionException {

        MapInfo[] nearby = rc.senseNearbyMapInfos();
        for (MapInfo tile : nearby) {
            if (!tile.hasRuin() && !tile.isWall()) {
                if (rc.canCompleteResourcePattern(tile.getMapLocation())) {
                    return true;
                }
            }
        }
        return false;
    }

    private void doCompleteResourcePattern() throws GameActionException {
        MapInfo[] nearby = rc.senseNearbyMapInfos();
        for (MapInfo tile : nearby) {
            if (!tile.hasRuin() && !tile.isWall()) {
                MapLocation loc = tile.getMapLocation();
                if (rc.canCompleteResourcePattern(loc)) {
                    rc.completeResourcePattern(loc);
                    return;
                }

                if (rc.canMarkResourcePattern(loc)) {
                    rc.markResourcePattern(loc);
                    return;
                }
            }
        }

        state = State.SEEK_DENSITY;
    }
}
