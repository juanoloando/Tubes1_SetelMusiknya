package mainbot;

import battlecode.common.*;

public class SoldierUnit extends BaseRobot {

    private enum State { BUILD_TOWER, PAINT, EXPLORE, INITIAL_PAINT }
    private State state = State.EXPLORE;
    private MapLocation homeTowerLoc = null;
    private boolean isRoleA = false; // Mengganti isLeftSide

    public SoldierUnit(RobotController rc) {
        super(rc);
        if (rc.getRoundNum() < 30) {
            this.state = State.INITIAL_PAINT;
        }
    }

    @Override
    protected void tick() throws GameActionException {
        updateTowerMemory();
        readIncomingMessages();
        decideState();
        executeState();
    }

    private void readIncomingMessages() throws GameActionException {
        for (Message msg : rc.readMessages(-1)) {
            int content = msg.getBytes();
            if ((content & 0x80000000) != 0) {
                int x = (content >> 16) & 0x7FFF;
                int y = content & 0xFFFF;
                addEnemyTower(new MapLocation(x, y));
            } else {
                int x = (content >> 16) & 0xFFFF;
                int y = content & 0xFFFF;
                if (x > 0 && y > 0 && x < rc.getMapWidth() && y < rc.getMapHeight()) {
                    addAlliedTower(new MapLocation(x, y));
                }
            }
        }
    }

    private void decideState() throws GameActionException {
        if (state == State.INITIAL_PAINT) return; 

        // Jika cat habis (0), fokus menjauh/eksplorasi tanpa cat
        if (rc.getPaint() <= 0) {
            state = State.EXPLORE;
            return;
        }

        if (findBestRuin() != null) {
            state = State.BUILD_TOWER;
            return;
        }

        if (hasPaintableTarget()) {
            state = State.PAINT;
            return;
        }

        state = State.EXPLORE;
    }

    private void executeState() throws GameActionException {
        switch (state) {
            case BUILD_TOWER:   doBuildTower();   break;
            case PAINT:         doPaint();        break;
            case EXPLORE:       doExplore();      break;
            case INITIAL_PAINT: doInitialPaint(); break;
        }
    }

    private void doBuildTower() throws GameActionException {
        MapLocation ruinLoc = findBestRuin();
        if (ruinLoc == null) {
            state = State.EXPLORE;
            return;
        }
        if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, ruinLoc)) {
            rc.completeTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, ruinLoc);
            exploreTarget = null;
            return;
        }
        if (rc.canMarkTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, ruinLoc)) {
            MapInfo ruinInfo = rc.senseMapInfo(ruinLoc);
            if (ruinInfo.getMark() == PaintType.EMPTY) {
                rc.markTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, ruinLoc);
            }
        }
        if (rc.isActionReady()) {
            boolean painted = paintPatternTile(ruinLoc);
            if (!painted && rc.isMovementReady()) moveTo(ruinLoc);
        } else if (rc.isMovementReady()) {
            moveTo(ruinLoc);
        }
    }

    private void doInitialPaint() throws GameActionException {
        if (homeTowerLoc == null) {
            RobotInfo[] nearby = rc.senseNearbyRobots(2, rc.getTeam());
            for (RobotInfo r : nearby) {
                if (r.getType().isTowerType()) {
                    homeTowerLoc = r.getLocation();
                    // PERBAIKAN: Gunakan ID untuk membagi peran, bukan koordinat X
                    isRoleA = (rc.getID() % 2 == 0);
                    break;
                }
            }
            if (homeTowerLoc == null) {
                state = State.EXPLORE;
                return;
            }
        }

        MapLocation bestTarget = null;
        int bestDist = Integer.MAX_VALUE;
        boolean jobDone = true;

        MapInfo[] area = rc.senseNearbyMapInfos(homeTowerLoc, 8);
        for (MapInfo tile : area) {
            MapLocation tLoc = tile.getMapLocation();
            
            // PERBAIKAN: Pembagian area secara simetris relatif terhadap Tower
            boolean isLeftOfTower = tLoc.x < homeTowerLoc.x;
            boolean isSameX = tLoc.x == homeTowerLoc.x;

            if (isRoleA) {
                // Peran A: Mengurus sisi kiri tower + setengah jalur tengah vertikal
                if (!isLeftOfTower && !(isSameX && tLoc.y < homeTowerLoc.y)) continue;
            } else {
                // Peran B: Mengurus sisi kanan tower + setengah jalur tengah sisanya
                if (isLeftOfTower && !(isSameX && tLoc.y >= homeTowerLoc.y)) continue;
            }

            if (tile.isWall() || tile.hasRuin() || tLoc.equals(homeTowerLoc)) continue;
            if (tile.getPaint().isAlly()) continue;

            jobDone = false;
            if (rc.canAttack(tLoc)) {
                if (rc.isActionReady()) {
                    rc.attack(tLoc);
                    return; 
                }
            } else {
                int dist = rc.getLocation().distanceSquaredTo(tLoc);
                if (dist < bestDist) {
                    bestDist = dist;
                    bestTarget = tLoc;
                }
            }
        }

        if (jobDone) {
            state = State.EXPLORE;
        } else if (bestTarget != null && rc.isMovementReady()) {
            moveTo(bestTarget);
        }
    }

    private MapLocation findBestRuin() throws GameActionException {
        MapInfo[] nearby = rc.senseNearbyMapInfos();
        MapLocation bestRuin = null;
        int bestScore = -1;
        for (MapInfo tile : nearby) {
            if (!tile.hasRuin()) continue;
            MapLocation ruinLoc = tile.getMapLocation();
            if (!rc.canSenseLocation(ruinLoc)) continue;
            RobotInfo robotAtRuin = rc.senseRobotAtLocation(ruinLoc);
            if (robotAtRuin != null) continue;
            int correctCount = countCorrectPatternTiles(ruinLoc);
            int dist = rc.getLocation().distanceSquaredTo(ruinLoc);
            int score = correctCount * 10 - dist / 5;
            if (score > bestScore) {
                bestScore = score;
                bestRuin = ruinLoc;
            }
        }
        return bestRuin;
    }

    private int countCorrectPatternTiles(MapLocation ruinLoc) throws GameActionException {
        int count = 0;
        try {
            MapInfo[] patternArea = rc.senseNearbyMapInfos(ruinLoc, 8);
            for (MapInfo t : patternArea) {
                if (t.getMark() != PaintType.EMPTY && t.getMark() == t.getPaint()) count++;
            }
        } catch (Exception e) { }
        return count;
    }

    private boolean paintPatternTile(MapLocation ruinLoc) throws GameActionException {
        MapInfo[] patternArea = rc.senseNearbyMapInfos(ruinLoc, 8);
        for (MapInfo t : patternArea) {
            if (t.getMark() == PaintType.EMPTY || t.getMark() == t.getPaint()) continue;
            MapLocation tLoc = t.getMapLocation();
            if (rc.canAttack(tLoc)) {
                rc.attack(tLoc, (t.getMark() == PaintType.ALLY_SECONDARY));
                return true;
            }
        }
        return false;
    }

    private boolean hasPaintableTarget() throws GameActionException {
        if (!rc.isActionReady()) return false;
        MapInfo[] nearby = rc.senseNearbyMapInfos(rc.getType().actionRadiusSquared);
        for (MapInfo tile : nearby) {
            if (!tile.getPaint().isAlly() && rc.canAttack(tile.getMapLocation())) return true;
        }
        return false;
    }

    private void doPaint() throws GameActionException {
        if (rc.isActionReady()) {
            MapLocation best = findBestPaintTarget();
            if (best != null) rc.attack(best);
        }
        if (rc.isMovementReady()) greedyMoveForPaint();
    }

    private MapLocation findBestPaintTarget() throws GameActionException {
        MapInfo[] candidates = rc.senseNearbyMapInfos(rc.getType().actionRadiusSquared);
        MapLocation bestEnemy = null;
        MapLocation bestEmpty = null;
        for (MapInfo tile : candidates) {
            if (!rc.canAttack(tile.getMapLocation())) continue;
            if (tile.getPaint().isEnemy()) {
                bestEnemy = tile.getMapLocation();
                break;
            }
            if (!tile.getPaint().isAlly() && bestEmpty == null) bestEmpty = tile.getMapLocation();
        }
        return bestEnemy != null ? bestEnemy : bestEmpty;
    }

    private void greedyMoveForPaint() throws GameActionException {
        Direction bestDir = null;
        int bestScore = Integer.MIN_VALUE;
        for (Direction dir : DIRS) {
            if (!rc.canMove(dir)) continue;
            MapLocation next = rc.getLocation().add(dir);
            int score = calcPaintMoveScore(next);
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

    private int calcPaintMoveScore(MapLocation loc) throws GameActionException {
        if (!rc.canSenseLocation(loc)) return 0;
        MapInfo info = rc.senseMapInfo(loc);
        if (info.isWall() || info.hasRuin()) return Integer.MIN_VALUE;
        int score = info.getPaint().isEnemy() ? 60 : (!info.getPaint().isAlly() ? 30 : -15);
        for (MapInfo nearby : rc.senseNearbyMapInfos(loc, 8)) {
            if (nearby.hasRuin()) { score += 80; break; }
        }
        return score - getTrailPenalty(loc);
    }

    private void doExplore() throws GameActionException {
    if (rc.getPaint() > 0 && rc.isActionReady()) {
        MapLocation paintTarget = findBestPaintTarget();
        if (paintTarget != null) rc.attack(paintTarget);
    }
    
    if (rc.getPaint() <= 10) { // Jika cat hampir habis atau habis
        moveAwayFromHome(); // Menjauh dari base sampai mati
    } else {
        greedyExplore(rc);
    }
}
}