package altbot1;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.PaintType;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;

public class Soldier {
    static MapLocation targetRuin = null;

    static void run(RobotController rc) throws GameActionException {
        BaseUnit.init(rc);
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());

        if (enemies.length > 0)
            Comms.pingDanger(rc, enemies[0].getLocation());

        if (rc.getPaint() <= rc.getType().paintCapacity * 0.25) {
            BaseUnit.seekRefuel(rc);
            return;
        }

        RobotInfo target = Heuristics.pickBestTarget(enemies, rc.getLocation());

        if (target != null) {
            boolean amIBuilding = targetRuin != null && rc.getLocation().distanceSquaredTo(targetRuin) <= 2;
            int dangerDist = amIBuilding ? 2 : 16;
            if (rc.getLocation().distanceSquaredTo(target.getLocation()) <= dangerDist) {
                if (rc.isActionReady() && rc.canAttack(target.getLocation()))
                    rc.attack(target.getLocation());
                if (rc.isMovementReady()) {
                    Direction toEnemy = rc.getLocation().directionTo(target.getLocation());
                    Navigation.moveGreedyMacro(rc, toEnemy, BaseUnit.history, false);
                    BaseUnit.recordHistory(rc);
                }
                return;
            }
        }

        targetRuin = null;
        int bestDist = Integer.MAX_VALUE;
        MapInfo[] infos = rc.senseNearbyMapInfos();
        for (MapInfo tile : infos) {
            if (Clock.getBytecodesLeft() < 1000)
                break;
            if (tile.hasRuin()) {
                MapLocation loc = tile.getMapLocation();
                RobotInfo r = rc.senseRobotAtLocation(loc);
                if (r != null && r.getType().isTowerType())
                    continue;
                if (Comms.isRuinClaimed(rc, loc) && (targetRuin == null || !loc.equals(targetRuin)))
                    continue;
                int d = rc.getLocation().distanceSquaredTo(loc);
                if (d < bestDist) {
                    bestDist = d;
                    targetRuin = loc;
                }
            }
        }

        if (targetRuin != null) {
            Comms.claimRuin(rc, targetRuin);
            if (rc.getLocation().distanceSquaredTo(targetRuin) > 2) {
                Navigation.moveGreedyMacro(rc, rc.getLocation().directionTo(targetRuin), BaseUnit.history, false);
                BaseUnit.recordHistory(rc);
            } else {
                UnitType tt = Heuristics.scoreTowerType(rc, targetRuin);
                if (rc.canMarkTowerPattern(tt, targetRuin))
                    rc.markTowerPattern(tt, targetRuin);

                if (rc.isActionReady()) {
                    MapInfo[] patternTiles = rc.senseNearbyMapInfos(targetRuin, 8);
                    for (MapInfo tile : patternTiles) {
                        if (!rc.isActionReady() || Clock.getBytecodesLeft() < 500)
                            break;
                        PaintType mark = tile.getMark();
                        PaintType paint = tile.getPaint();
                        if (mark != PaintType.EMPTY && mark != paint && rc.canAttack(tile.getMapLocation())) {
                            rc.attack(tile.getMapLocation(), mark == PaintType.ALLY_SECONDARY);
                            break;
                        }
                    }
                }
                if (rc.canCompleteTowerPattern(tt, targetRuin)) {
                    rc.completeTowerPattern(tt, targetRuin);
                    targetRuin = null;
                }
            }
            return;
        }

        if (rc.isActionReady() && rc.getNumberTowers() >= 3) {
            MapLocation myLoc = rc.getLocation();
            boolean srpBuilt = false;

            if (rc.canMarkResourcePattern(myLoc)) {
                rc.markResourcePattern(myLoc);
                srpBuilt = true;
            }
            if (rc.isActionReady()) {
                for (MapInfo tile : rc.senseNearbyMapInfos(myLoc, 8)) {
                    if (!rc.isActionReady() || Clock.getBytecodesLeft() < 500)
                        break;
                    PaintType mark = tile.getMark();
                    if (mark != PaintType.EMPTY && mark != tile.getPaint() && rc.canAttack(tile.getMapLocation())) {
                        rc.attack(tile.getMapLocation(), mark == PaintType.ALLY_SECONDARY);
                        srpBuilt = true;
                        break;
                    }
                }
            }
            if (rc.canCompleteResourcePattern(myLoc)) {
                rc.completeResourcePattern(myLoc);
                srpBuilt = true;
            }

            if (srpBuilt)
                return;
        }

        if (rc.isMovementReady()) {
            Navigation.moveGreedyMacro(rc, BaseUnit.getZigZagMacro(rc, false), BaseUnit.history, false);
            BaseUnit.recordHistory(rc);
        }
        BaseUnit.microPaint(rc);
    }
}
