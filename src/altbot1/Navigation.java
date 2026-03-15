package altbot1;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.PaintType;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;

public class Navigation {
    static final Direction[] DIRS = { Direction.NORTH, Direction.NORTHEAST, Direction.EAST, Direction.SOUTHEAST,
            Direction.SOUTH, Direction.SOUTHWEST, Direction.WEST, Direction.NORTHWEST };

    static boolean moveGreedyMacro(RobotController rc, Direction targetDir, MapLocation[] history,
            boolean isSplasher) throws GameActionException {
        if (!rc.isMovementReady())
            return false;
        if (targetDir == null)
            return moveRandom(rc);

        MapLocation myLoc = rc.getLocation();
        Direction bestDir = null;
        int bestScore = Integer.MIN_VALUE;
        MapLocation center = new MapLocation(rc.getMapWidth() / 2, rc.getMapHeight() / 2);

        RobotInfo[] allies = rc.senseNearbyRobots(16, rc.getTeam());
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());

        for (Direction dir : DIRS) {
            if (!rc.canMove(dir))
                continue;
            MapLocation nextLoc = myLoc.add(dir);
            int score = 0;

            if (dir == targetDir)
                score += 20;
            else if (dir == targetDir.rotateLeft() || dir == targetDir.rotateRight())
                score += 10;
            else if (dir == targetDir.rotateLeft().rotateLeft() || dir == targetDir.rotateRight().rotateRight())
                score += 2;

            if (rc.canSenseLocation(nextLoc)) {
                MapInfo info = rc.senseMapInfo(nextLoc);
                PaintType paint = info.getPaint();

                if (paint.isAlly())
                    score += 8;
                else if (paint == PaintType.EMPTY)
                    score += 10;
                else
                    score -= 15;

                if (nextLoc.distanceSquaredTo(center) < myLoc.distanceSquaredTo(center))
                    score += 5;

                if (info.hasRuin()) {
                    RobotInfo r = rc.senseRobotAtLocation(nextLoc);
                    if (r == null || !r.getType().isTowerType())
                        score += 50;
                }
            }

            for (RobotInfo enemy : enemies) {
                if (enemy.getType().isTowerType()) {
                    if (nextLoc.distanceSquaredTo(enemy.getLocation()) <= enemy.getType().actionRadiusSquared) {
                        score -= 1000;
                    }
                }
            }

            if (history != null) {
                for (MapLocation h : history) {
                    if (h != null && h.equals(nextLoc)) {
                        score -= 60;
                        break;
                    }
                }
            }

            if (isSplasher) {
                for (RobotInfo ally : allies) {
                    if (ally.getType() == UnitType.SPLASHER && nextLoc.distanceSquaredTo(ally.getLocation()) <= 8) {
                        score -= 20;
                    }
                }
            }

            if (score > bestScore) {
                bestScore = score;
                bestDir = dir;
            }
        }
        if (bestDir != null) {
            rc.move(bestDir);
            return true;
        }
        return false;
    }

    static boolean moveRandom(RobotController rc) throws GameActionException {
        if (!rc.isMovementReady())
            return false;
        int start = rc.getRoundNum() % 8;
        for (int i = 0; i < 8; i++) {
            Direction d = DIRS[(start + i) % 8];
            if (rc.canMove(d)) {
                rc.move(d);
                return true;
            }
        }
        return false;
    }
}
