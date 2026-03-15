package altbot1;

import battlecode.common.*;

public class Mopper {
    static void run(RobotController rc) throws GameActionException {
        BaseUnit.init(rc);
        if (rc.getPaint() <= rc.getType().paintCapacity * 0.25) {
            BaseUnit.seekRefuel(rc);
            return;
        }

        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        RobotInfo target = Heuristics.pickBestTarget(enemies, rc.getLocation());

        if (target != null && rc.isActionReady()) {
            MapLocation tLoc = target.getLocation();
            Direction dir = rc.getLocation().directionTo(tLoc);
            if (rc.getLocation().distanceSquaredTo(tLoc) <= 2 && rc.canMopSwing(dir))
                rc.mopSwing(dir);
            else if (rc.canAttack(tLoc))
                rc.attack(tLoc);
        }

        if (!rc.isActionReady() && target == null) {
            MapInfo[] tiles = rc.senseNearbyMapInfos();
            for (MapInfo tile : tiles) {
                if (Clock.getBytecodesLeft() < 500)
                    break;
                if (tile.getPaint().isEnemy() && rc.canAttack(tile.getMapLocation())) {
                    rc.attack(tile.getMapLocation());
                    break;
                }
            }
        }

        if (rc.isMovementReady()) {
            if (target != null)
                Navigation.moveGreedyMacro(rc, rc.getLocation().directionTo(target.getLocation()), BaseUnit.history,
                        false);
            else
                Navigation.moveGreedyMacro(rc, BaseUnit.getZigZagMacro(rc, false), BaseUnit.history, false);
            BaseUnit.recordHistory(rc);
        }
    }
}
