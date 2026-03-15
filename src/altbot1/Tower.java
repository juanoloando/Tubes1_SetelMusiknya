package altbot1;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;

public class Tower {
    static void run(RobotController rc) throws GameActionException {
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        RobotInfo bestTarget = Heuristics.pickBestTarget(enemies, rc.getLocation());
        if (bestTarget != null && rc.canAttack(bestTarget.getLocation())) {
            rc.attack(bestTarget.getLocation());
        }

        if (rc.getRoundNum() <= 2) {
            MapLocation myLoc = rc.getLocation();
            MapLocation rot = new MapLocation(rc.getMapWidth() - 1 - myLoc.x, rc.getMapHeight() - 1 - myLoc.y);
            Comms.broadcastEnemySpawn(rc, rot);
        }

        UnitType type = rc.getType();

        if (type == UnitType.LEVEL_ONE_MONEY_TOWER || type == UnitType.LEVEL_TWO_MONEY_TOWER) {
            if (rc.getChips() > 2500 && rc.getPaint() < 500) {
                RobotInfo[] allies = rc.senseNearbyRobots(2, rc.getTeam());
                for (RobotInfo ally : allies) {
                    if (ally.getType() == UnitType.SOLDIER) {
                        rc.disintegrate();
                        return;
                    }
                }
            }
        }

        if (rc.isActionReady() && rc.getChips() >= 3000 && type.toString().contains("LEVEL_ONE")
                && rc.canUpgradeTower(rc.getLocation())) {
            rc.upgradeTower(rc.getLocation());
            return;
        }

        if (rc.isActionReady()) {
            MapLocation spawnLoc = null;
            for (Direction d : Navigation.DIRS) {
                MapLocation c = rc.getLocation().add(d);
                if (rc.onTheMap(c) && rc.sensePassability(c) && !rc.isLocationOccupied(c)) {
                    spawnLoc = c;
                    break;
                }
            }

            if (spawnLoc != null) {
                int p = rc.getPaint(), c = rc.getChips(), r = rc.getRoundNum();
                int enemyPaintCount = 0;
                MapInfo[] tiles = rc.senseNearbyMapInfos();
                for (MapInfo tile : tiles) {
                    if (Clock.getBytecodesLeft() < 1000)
                        break;
                    if (tile.getPaint().isEnemy())
                        enemyPaintCount++;
                }

                MapLocation danger = Comms.readDanger(rc);
                boolean globalPanic = danger != null && rc.getLocation().distanceSquaredTo(danger) <= 100;

                if ((enemyPaintCount > 8 || globalPanic) && p >= 100 && c >= 300
                        && rc.canBuildRobot(UnitType.MOPPER, spawnLoc)) {
                    rc.buildRobot(UnitType.MOPPER, spawnLoc);
                } else if (c > 1000 && p > 600) {
                    int ratio = r % 10;
                    if (ratio < 1 && rc.canBuildRobot(UnitType.MOPPER, spawnLoc))
                        rc.buildRobot(UnitType.MOPPER, spawnLoc);
                    else if (ratio < 4 && rc.canBuildRobot(UnitType.SOLDIER, spawnLoc))
                        rc.buildRobot(UnitType.SOLDIER, spawnLoc);
                    else if (rc.canBuildRobot(UnitType.SPLASHER, spawnLoc))
                        rc.buildRobot(UnitType.SPLASHER, spawnLoc);
                } else if (p >= 200 && c >= 250 && rc.canBuildRobot(UnitType.SOLDIER, spawnLoc)) {
                    rc.buildRobot(UnitType.SOLDIER, spawnLoc);
                }
            }
        }
    }
}
