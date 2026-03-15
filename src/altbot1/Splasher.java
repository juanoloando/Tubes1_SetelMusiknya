package altbot1;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.PaintType;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;

public class Splasher {
    static void run(RobotController rc) throws GameActionException {
        BaseUnit.init(rc);
        if (rc.getPaint() <= rc.getType().paintCapacity * 0.25) {
            BaseUnit.seekRefuel(rc);
            return;
        }

        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        int threshold = Heuristics.getSplasherThreshold(rc, enemies.length > 0);

        MapLocation bestTarget = null;
        int bestScore = Integer.MIN_VALUE;
        MapLocation myLoc = rc.getLocation();

        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                if (Clock.getBytecodesLeft() < 1500)
                    break;
                MapLocation candidate = myLoc.translate(dx, dy);
                if (!rc.canSenseLocation(candidate))
                    continue;
                int score = Heuristics.scoreSplasherTarget(rc, candidate);
                if (score > bestScore) {
                    bestScore = score;
                    bestTarget = candidate;
                }
            }
        }

        if (bestTarget != null && bestScore >= threshold && rc.canAttack(bestTarget)) {
            rc.attack(bestTarget);
        }

        if (rc.isMovementReady()) {
            MapLocation cluster = findUnpaintedCluster(rc);
            if (cluster != null) {
                Navigation.moveGreedyMacro(rc, myLoc.directionTo(cluster), BaseUnit.history, true);
            } else {
                Navigation.moveGreedyMacro(rc, BaseUnit.getZigZagMacro(rc, true), BaseUnit.history, true);
            }
            BaseUnit.recordHistory(rc);
        }
        BaseUnit.microPaint(rc);
    }

    static MapLocation findUnpaintedCluster(RobotController rc) throws GameActionException {
        MapLocation bestLoc = null;
        int maxScore = -1;
        MapInfo[] tiles = rc.senseNearbyMapInfos(16);
        for (MapInfo tile : tiles) {
            if (Clock.getBytecodesLeft() < 1500)
                break;
            if (!tile.isPassable() || tile.getPaint().isAlly())
                continue;
            int localScore = 0;
            MapLocation loc = tile.getMapLocation();
            Direction[] dirs = { Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST };
            for (Direction d : dirs) {
                MapLocation adj = loc.add(d);
                if (rc.canSenseLocation(adj)) {
                    PaintType pt = rc.senseMapInfo(adj).getPaint();
                    if (pt == PaintType.EMPTY)
                        localScore += 2;
                    else if (pt.isEnemy())
                        localScore += 3;
                }
            }
            if (localScore > maxScore) {
                maxScore = localScore;
                bestLoc = loc;
            }
        }
        return bestLoc;
    }
}
