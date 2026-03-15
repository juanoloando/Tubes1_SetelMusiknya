package altbot1;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.PaintType;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;

public class BaseUnit {
    static MapLocation[] history = new MapLocation[5];
    static int histIdx = 0;
    static MapLocation predictedEnemySpawn = null;
    static Direction macroDirection = null;
    static MapLocation mySpawnLocation = null;

    static void init(RobotController rc) throws GameActionException {
        if (mySpawnLocation == null)
            mySpawnLocation = rc.getLocation();
        if (macroDirection == null) {
            predictedEnemySpawn = Comms.getEnemySpawn(rc);
            if (predictedEnemySpawn != null) {
                macroDirection = rc.getLocation().directionTo(predictedEnemySpawn);
                if (macroDirection == Direction.CENTER)
                    macroDirection = Navigation.DIRS[rc.getID() % 8];
            } else {
                int cx = rc.getMapWidth() / 2, cy = rc.getMapHeight() / 2;
                MapLocation focal = new MapLocation(cx, cy);
                macroDirection = rc.getLocation().directionTo(focal);
                if (macroDirection == Direction.CENTER)
                    macroDirection = Navigation.DIRS[rc.getID() % 8];
            }
        }
    }

    static Direction getZigZagMacro(RobotController rc, boolean isSplasher) throws GameActionException {
        MapLocation nextMacro = rc.getLocation().add(macroDirection);

        if (rc.canSenseLocation(nextMacro)) {
            MapInfo info = rc.senseMapInfo(nextMacro);
            if (!info.isPassable()) {
                macroDirection = macroDirection.rotateRight().rotateRight();
            } else if (isSplasher && info.getPaint().isAlly()) {
                if (RobotPlayer.rng.nextInt(10) < 4) {
                    macroDirection = RobotPlayer.rng.nextBoolean() ? macroDirection.rotateRight() : macroDirection.rotateLeft();
                }
            }
        } else if (!rc.onTheMap(nextMacro)) {
            macroDirection = macroDirection.rotateRight().rotateRight();
        }

        int cycle = (rc.getRoundNum() / 5) % 3;
        if (cycle == 0)
            return macroDirection.rotateLeft();
        if (cycle == 1)
            return macroDirection.rotateRight();
        return macroDirection;
    }

    static void recordHistory(RobotController rc) {
        history[histIdx] = rc.getLocation();
        histIdx = (histIdx + 1) % 5;
    }

    static boolean seekRefuel(RobotController rc) throws GameActionException {
        RobotInfo[] towers = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo r : towers) {
            if (r.getType().isTowerType() && r.getPaintAmount() > 0) {
                if (rc.getLocation().distanceSquaredTo(r.getLocation()) <= 2) {
                    if (!rc.isActionReady())
                        return true;
                    int need = rc.getType().paintCapacity - rc.getPaint();
                    int can = Math.min(need, r.getPaintAmount());
                    if (can > 0 && rc.canTransferPaint(r.getLocation(), -can)) {
                        rc.transferPaint(r.getLocation(), -can);
                        return true;
                    }
                } else {
                    Navigation.moveGreedyMacro(rc, rc.getLocation().directionTo(r.getLocation()), history, false);
                    return true;
                }
            }
        }
        if (mySpawnLocation != null && rc.isMovementReady()) {
            Navigation.moveGreedyMacro(rc, rc.getLocation().directionTo(mySpawnLocation), history, false);
            return true;
        }
        return false;
    }

    static void microPaint(RobotController rc) throws GameActionException {
        if (!rc.isActionReady())
            return;
        MapLocation myLoc = rc.getLocation();

        PaintType ptSelf = rc.senseMapInfo(myLoc).getPaint();
        if (ptSelf != PaintType.ALLY_PRIMARY && ptSelf != PaintType.ALLY_SECONDARY && rc.canAttack(myLoc)) {
            rc.attack(myLoc);
            return;
        }

        if (macroDirection != null) {
            MapLocation front = myLoc.add(macroDirection);
            if (rc.canSenseLocation(front) && rc.canAttack(front)) {
                PaintType ptFront = rc.senseMapInfo(front).getPaint();
                if (ptFront == PaintType.EMPTY || ptFront.isEnemy()) {
                    rc.attack(front);
                    return;
                }
            }
        }

        for (Direction d : Navigation.DIRS) {
            MapLocation adj = myLoc.add(d);
            if (rc.canSenseLocation(adj) && rc.canAttack(adj)) {
                PaintType ptAdj = rc.senseMapInfo(adj).getPaint();
                if (ptAdj == PaintType.EMPTY || ptAdj.isEnemy()) {
                    rc.attack(adj);
                    return;
                }
            }
        }
    }
}
