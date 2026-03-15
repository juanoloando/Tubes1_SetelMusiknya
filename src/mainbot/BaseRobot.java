package mainbot;

import battlecode.common.*;
import java.util.Random;

public abstract class BaseRobot {

    protected RobotController rc;
    protected Random rng;

    protected static final Direction[] DIRS = {
        Direction.NORTH, Direction.NORTHEAST, Direction.EAST, Direction.SOUTHEAST,
        Direction.SOUTH, Direction.SOUTHWEST, Direction.WEST, Direction.NORTHWEST,
    };

    protected static MapLocation[] trail = new MapLocation[50];
    protected static int trailIdx = 0;

    protected static MapLocation[] alliedTowers = new MapLocation[30];
    protected static int towerCount = 0;

    protected static MapLocation[] enemyTowers = new MapLocation[10];
    protected static int enemyTowerCount = 0;

    protected static MapLocation exploreTarget = null;

    public BaseRobot(RobotController rc) {
        this.rc = rc;
        this.rng = new Random(rc.getID());
    }

    
    public void run() throws GameActionException {
        while (true) {
            try {
                tick();
            } catch (GameActionException e) {
                System.out.println("[MAINBOT] GameActionException: " + e.getMessage());
            } catch (Exception e) {
                System.out.println("[MAINBOT] Exception: " + e.getMessage());
            } finally {
                Clock.yield();
            }
        }
    }
    
    protected abstract void tick() throws GameActionException;

    protected void updateTowerMemory() throws GameActionException {
        RobotInfo[] nearby = rc.senseNearbyRobots(-1);
        for (RobotInfo robot : nearby) {
            if (robot.getType().isTowerType()) {
                if (robot.getTeam() == rc.getTeam()) {
                    addAlliedTower(robot.getLocation());
                } else {
                    addEnemyTower(robot.getLocation());
                }
            }
        }
    }

    protected void addAlliedTower(MapLocation loc) {
        for (int i = 0; i < towerCount; i++) {
            if (alliedTowers[i].equals(loc)) return;
        }
        if (towerCount < alliedTowers.length) {
            alliedTowers[towerCount++] = loc;
        }
    }

    protected void addEnemyTower(MapLocation loc) {
        for (int i = 0; i < enemyTowerCount; i++) {
            if (enemyTowers[i].equals(loc)) return;
        }
        if (enemyTowerCount < enemyTowers.length) {
            enemyTowers[enemyTowerCount++] = loc;
        }
    }




    
    protected void moveTo(MapLocation target) throws GameActionException {
        if (!rc.isMovementReady()) return;

        Direction bestDir = null;
        int bestScore = Integer.MAX_VALUE;

        for (Direction dir : DIRS) {
            if (!rc.canMove(dir)) continue;
            MapLocation next = rc.getLocation().add(dir);

            int distScore = next.distanceSquaredTo(target);
            int trailPenalty = getTrailPenalty(next);

            int total = distScore + trailPenalty;
            if (total < bestScore) {
                bestScore = total;
                bestDir = dir;
            }
        }

        if (bestDir != null) {
            rc.move(bestDir);
            recordTrail(rc.getLocation());
        }
    }

    
    protected void roamExplore(RobotController rc) throws GameActionException {
        if (!rc.isMovementReady()) return;

        if (exploreTarget == null || rc.getLocation().distanceSquaredTo(exploreTarget) < 4) {
            exploreTarget = pickExploreTarget(rc);
        }

        if (exploreTarget != null) {
            moveTo(exploreTarget);
        } else {
            randomMove();
        }
    }

    
    protected MapLocation pickExploreTarget(RobotController rc) {
        int w = rc.getMapWidth();
        int h = rc.getMapHeight();
        MapLocation me = rc.getLocation();

        MapLocation[] candidates = {
            new MapLocation(2, 2),
            new MapLocation(w - 3, 2),
            new MapLocation(2, h - 3),
            new MapLocation(w - 3, h - 3),
            new MapLocation(w / 2, h / 2),
            new MapLocation(w / 4, h / 4),
            new MapLocation(3 * w / 4, h / 4),
            new MapLocation(w / 4, 3 * h / 4),
            new MapLocation(3 * w / 4, 3 * h / 4),
        };

        MapLocation best = null;
        int bestDist = -1;

        for (MapLocation c : candidates) {
            if (!rc.onTheMap(c)) continue;
            int dist = me.distanceSquaredTo(c);
            if (dist > bestDist) {
                bestDist = dist;
                best = c;
            }
        }

        if (bestDist < 50) {
            int rx = rng.nextInt(w);
            int ry = rng.nextInt(h);
            best = new MapLocation(rx, ry);
        }

        return best;
    }

    protected void randomMove() throws GameActionException {
        if (!rc.isMovementReady()) return;
        Direction d = DIRS[rng.nextInt(DIRS.length)];
        if (rc.canMove(d)) rc.move(d);
    }




    protected void recordTrail(MapLocation loc) {
        trail[trailIdx] = loc;
        trailIdx = (trailIdx + 1) % trail.length;
    }

    protected int getTrailPenalty(MapLocation loc) {
        int penalty = 0;
        for (MapLocation t : trail) {
            if (t != null && t.equals(loc)) {
                penalty += 800;
            }
        }
        return penalty;
    }

    protected MapLocation findNearestAlliedTower() {
        MapLocation nearest = null;
        int minDist = Integer.MAX_VALUE;
        for (int i = 0; i < towerCount; i++) {
            int d = rc.getLocation().distanceSquaredTo(alliedTowers[i]);
            if (d < minDist) {
                minDist = d;
                nearest = alliedTowers[i];
            }
        }
        return nearest;
    }

    protected void moveAwayFromHome() throws GameActionException {
        if (!rc.isMovementReady()) return;
        MapLocation home = findNearestAlliedTower();
        if (home == null) {
            randomMove();
            return;
        }

        Direction bestDir = null;
        int maxDist = -1;

        for (Direction dir : DIRS) {
            if (!rc.canMove(dir)) continue;
            MapLocation next = rc.getLocation().add(dir);
            int dist = next.distanceSquaredTo(home);
            if (dist > maxDist) {
                maxDist = dist;
                bestDir = dir;
            }
        }

        if (bestDir != null) {
            rc.move(bestDir);
        }
    }
}
