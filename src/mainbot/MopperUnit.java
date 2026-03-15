package mainbot;

import battlecode.common.*;

public class MopperUnit extends BaseRobot {

    private enum State { MOB_COMBAT, CLEAN_ENEMY_PAINT, SUPPORT_ALLY, PATROL }
    private State state = State.PATROL;

    public MopperUnit(RobotController rc) {
        super(rc);
    }

    @Override
    protected void tick() throws GameActionException {
        updateTowerMemory();
        reportEnemyTowers();
        decideState();
        executeState();
    }

    
    private void reportEnemyTowers() throws GameActionException {
        if (enemyTowerCount == 0) return;
        MapLocation tower = findNearestAlliedTower();
        if (tower == null) return;

        for (int i = 0; i < enemyTowerCount; i++) {
            MapLocation enemyLoc = enemyTowers[i];
            if (rc.canSendMessage(tower)) {

                int encoded = 0x80000000 | (enemyLoc.x << 16) | enemyLoc.y;
                rc.sendMessage(tower, encoded);
            }
        }
    }

    private void decideState() throws GameActionException {
        if (rc.getPaint() > 0) {
            if (hasMopSwingOpportunity()) {
                state = State.MOB_COMBAT;
                return;
            }
            if (hasEnemyPaintNearby()) {
                state = State.CLEAN_ENEMY_PAINT;
                return;
            }
        }

        state = State.PATROL;
    }

    private void executeState() throws GameActionException {
        switch (state) {
            case MOB_COMBAT:        doMobCombat();         break;
            case CLEAN_ENEMY_PAINT: doCleanEnemyPaint();   break;
            case SUPPORT_ALLY:      doSupportAlly();       break;
            case PATROL:            doPatrol();            break;
        }
    }

    private boolean hasMopSwingOpportunity() throws GameActionException {
        for (Direction dir : DIRS) {
            if (rc.canMopSwing(dir) && countMopSwingHits(dir) > 0) {
                return true;
            }
        }
        return false;
    }

    
    private void doMobCombat() throws GameActionException {
        if (rc.isActionReady()) {
            Direction bestDir = null;
            int bestHits = 0;

            for (Direction dir : DIRS) {
                if (!rc.canMopSwing(dir)) continue;
                int hits = countMopSwingHits(dir);
                if (hits > bestHits) {
                    bestHits = hits;
                    bestDir = dir;
                }
            }

            if (bestDir != null) {
                rc.mopSwing(bestDir);
            }
        }

        if (rc.isMovementReady()) {
            chaseEnemy();
        }
    }

    
    private int countMopSwingHits(Direction dir) throws GameActionException {
        MapLocation me = rc.getLocation();
        Direction left = dir.rotateLeft();
        Direction right = dir.rotateRight();

        int hits = 0;
        MapLocation[] swingTiles = {
            me.add(dir).add(left),
            me.add(dir),
            me.add(dir).add(right)
        };

        for (MapLocation tile : swingTiles) {
            if (!rc.onTheMap(tile)) continue;
            if (!rc.canSenseLocation(tile)) continue;
            RobotInfo robot = rc.senseRobotAtLocation(tile);
            if (robot != null && robot.getTeam() != rc.getTeam()) {
                hits++;
            }
        }
        return hits;
    }




    private boolean hasEnemyPaintNearby() throws GameActionException {
        MapInfo[] nearby = rc.senseNearbyMapInfos(rc.getType().actionRadiusSquared);
        for (MapInfo tile : nearby) {
            if (tile.getPaint().isEnemy()) return true;
        }
        return false;
    }

    
    private void doCleanEnemyPaint() throws GameActionException {
        if (rc.isActionReady()) {
            MapLocation best = findBestCleanTarget();
            if (best != null) {
                rc.attack(best);
            }
        }
        if (rc.isMovementReady()) {
            seekEnemyPaint();
        }
    }

    private MapLocation findBestCleanTarget() throws GameActionException {
        MapInfo[] candidates = rc.senseNearbyMapInfos(rc.getType().actionRadiusSquared);
        MapLocation bestTarget = null;
        int bestScore = Integer.MIN_VALUE;

        MapLocation nearestAllied = findNearestAlliedTower();

        for (MapInfo tile : candidates) {
            if (!tile.getPaint().isEnemy()) continue;
            if (!rc.canAttack(tile.getMapLocation())) continue;

            int score = 0;

            if (nearestAllied != null) {
                int distToBase = tile.getMapLocation().distanceSquaredTo(nearestAllied);
                score += Math.max(0, 100 - distToBase);
            }

            int adjacentEnemy = countAdjacentEnemyPaint(tile.getMapLocation());
            score += adjacentEnemy * 5;

            if (score > bestScore) {
                bestScore = score;
                bestTarget = tile.getMapLocation();
            }
        }

        return bestTarget;
    }

    private int countAdjacentEnemyPaint(MapLocation loc) throws GameActionException {
        int count = 0;
        for (Direction dir : DIRS) {
            MapLocation adj = loc.add(dir);
            if (!rc.canSenseLocation(adj)) continue;
            if (rc.senseMapInfo(adj).getPaint().isEnemy()) count++;
        }
        return count;
    }

    private void seekEnemyPaint() throws GameActionException {

        Direction bestDir = null;
        int bestScore = Integer.MIN_VALUE;

        for (Direction dir : DIRS) {
            if (!rc.canMove(dir)) continue;
            MapLocation next = rc.getLocation().add(dir);

            int score = 0;
            MapInfo[] nearNext = rc.senseNearbyMapInfos(next, 4);
            for (MapInfo t : nearNext) {
                if (t.getPaint().isEnemy()) score += 10;
            }

            score -= getTrailPenalty(next);

            if (score > bestScore) {
                bestScore = score;
                bestDir = dir;
            }
        }

        if (bestDir != null) {
            rc.move(bestDir);
            recordTrail(rc.getLocation());
        }
    }




    private boolean hasAllyToSupport() throws GameActionException {
        if (rc.getPaint() < 50) return false;
        RobotInfo[] allies = rc.senseNearbyRobots(2, rc.getTeam());
        for (RobotInfo ally : allies) {
            if (!ally.getType().isTowerType() && ally.getPaintAmount() < ally.getType().paintCapacity * 0.2) {
                return true;
            }
        }
        return false;
    }

    private void doSupportAlly() throws GameActionException {
        if (!rc.isActionReady()) return;
        RobotInfo[] allies = rc.senseNearbyRobots(2, rc.getTeam());
        for (RobotInfo ally : allies) {
            if (!ally.getType().isTowerType() &&
                ally.getPaintAmount() < ally.getType().paintCapacity * 0.2) {
                int donation = Math.min(40, rc.getPaint() - 50);
                if (donation > 0 && rc.canTransferPaint(ally.getLocation(), donation)) {
                    rc.transferPaint(ally.getLocation(), donation);
                    return;
                }
            }
        }
    }

    private void doPatrol() throws GameActionException {
    if (rc.getPaint() <= 0) {
        moveAwayFromHome();
    } else {
        roamExplore(rc);
    }
}

    private void chaseEnemy() throws GameActionException {
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (enemies.length > 0) {
            moveTo(enemies[0].getLocation());
        } else {
            seekEnemyPaint();
        }
    }
}
