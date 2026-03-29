package alternative_bots_1;

import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.PaintType;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;

public class Heuristics {
    static int getSplasherThreshold(RobotController rc, boolean enemyNearby) {
        if (rc.getPaint() > rc.getType().paintCapacity * 0.7)
            return 2;
        if (rc.getPaint() > rc.getType().paintCapacity * 0.4)
            return 3;
        if (rc.getRoundNum() > 1500)
            return 3;
        return enemyNearby ? 25 : 5;
    }

    static int scoreSplasherTarget(RobotController rc, MapLocation center) throws GameActionException {
        int score = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                MapLocation tile = center.translate(dx, dy);
                if (!rc.canSenseLocation(tile))
                    continue;
                MapInfo info = rc.senseMapInfo(tile);
                if (!info.isPassable() && !info.hasRuin())
                    continue;
                PaintType paint = info.getPaint();
                if (paint == PaintType.EMPTY)
                    score += 2;
                else if (paint.isAlly())
                    score -= 3;
                else
                    score += 15;
            }
        }
        return score;
    }

    static int scoreAttackTarget(RobotInfo enemy) {
        UnitType type = enemy.getType();
        if (type == UnitType.SPLASHER)
            return 200;
        if (type.isTowerType())
            return 80;
        if (type == UnitType.MOPPER)
            return 70;
        if (type == UnitType.SOLDIER)
            return 50;
        return 30;
    }

    static RobotInfo pickBestTarget(RobotInfo[] enemies, MapLocation myLoc) {
        RobotInfo best = null;
        int bestScore = Integer.MIN_VALUE;
        int bestDist = Integer.MAX_VALUE;
        for (RobotInfo enemy : enemies) {
            int score = scoreAttackTarget(enemy);
            int dist = myLoc.distanceSquaredTo(enemy.getLocation());
            if (score > bestScore || (score == bestScore && dist < bestDist)) {
                bestScore = score;
                bestDist = dist;
                best = enemy;
            }
        }
        return best;
    }

    static UnitType scoreTowerType(RobotController rc, MapLocation ruinLoc) throws GameActionException {
        int currentChips = rc.getChips();
        int currentPaint = rc.getPaint();
        int round = rc.getRoundNum();

        boolean isFrontline = false;
        int allyPaintCount = 0;
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos(ruinLoc, 16);
        for (MapInfo tile : nearbyTiles) {
            if (tile.getPaint().isEnemy()) {
                isFrontline = true;
                break;
            }
            if (tile.getPaint().isAlly()) {
                allyPaintCount++;
            }
        }

        boolean isSecured = !isFrontline && allyPaintCount >= 8;

        if (currentPaint < 1000 || round <= 100)
            return UnitType.LEVEL_ONE_PAINT_TOWER;

        if (isFrontline && currentChips >= 2000 && currentPaint >= 1000)
            return UnitType.LEVEL_ONE_DEFENSE_TOWER;

        if (isSecured && currentChips >= 1500) {
            return UnitType.LEVEL_ONE_PAINT_TOWER;
        }

        int paintTowers = 0;
        int moneyTowers = 0;
        RobotInfo[] allNearby = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo r : allNearby) {
            UnitType t = r.getType();
            if (t == UnitType.LEVEL_ONE_PAINT_TOWER || t == UnitType.LEVEL_TWO_PAINT_TOWER)
                paintTowers++;
            else if (t == UnitType.LEVEL_ONE_MONEY_TOWER || t == UnitType.LEVEL_TWO_MONEY_TOWER)
                moneyTowers++;
        }

        if (paintTowers < 2)
            return UnitType.LEVEL_ONE_PAINT_TOWER;
        if (moneyTowers == 0)
            return UnitType.LEVEL_ONE_MONEY_TOWER;

        if ((double) paintTowers / moneyTowers < 2.0)
            return UnitType.LEVEL_ONE_PAINT_TOWER;
        return UnitType.LEVEL_ONE_MONEY_TOWER;
    }
}
