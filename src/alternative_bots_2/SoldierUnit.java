package alternative_bots_2;

import battlecode.common.*;

/*
 Unit utama yang bertugas menjelajah, mengecat tile, dan membangun tower di ruin.
 Menggunakan state machine dengan prioritas: refill > selesaikan tower > bangun tower
 > cat tile musuh > cat tile kosong > jelajah > scout musuh.
 */
public class SoldierUnit extends BaseRobot {

    private enum State { REFILL, COMPLETE_TOWER, BUILD_TOWER, PAINT_ENEMY, PAINT_EMPTY, EXPLORE, SCOUT_ENEMY }
    private State state = State.EXPLORE;

    // Jenis penugasan scout: 0=tidak, 1=rotasional, 2=refleksi-X, 3=refleksi-Y
    private int scoutType = 0;
    private boolean scoutDone = false;

    // Ruin yang sedang menjadi target pembangunan tower
    private MapLocation targetRuin = null;

    public SoldierUnit(RobotController rc) {
        super(rc);
        // Soldier ke-1 → scout rotasional, ke-2 → refleksi-X, ke-3 → refleksi-Y
        int n = SharedMemory.soldierSpawned;
        if (n <= 3 && SharedMemory.predictedEnemyTower != null) {
            scoutType = n;
        }
    }

    @Override
    protected void tick() throws GameActionException {
        sense();
        readMessages();
        checkSymmetryConfirmation();

        if (Clock.getBytecodesLeft() > 5000) decide();
        if (Clock.getBytecodesLeft() > 3000) execute();
    }

    /*
     Jika robot ini melihat cat musuh dan simetri peta belum diketahui,
     coba konfirmasi simetri berdasarkan posisi cat musuh yang terlihat.
     Mengetahui simetri lebih awal memungkinkan seluruh armada langsung
     mengarahkan gerakan ke lokasi tower musuh yang benar.
     */
    private void checkSymmetryConfirmation() throws GameActionException {
        if (SharedMemory.confirmedSymmetry != 0) return;
        if (Clock.getBytecodesLeft() < 2000) return;

        MapLocation me = rc.getLocation();
        int w = SharedMemory.mapWidth;
        int h = SharedMemory.mapHeight;

        // Cocokkan cat musuh yang terlihat dengan salah satu prediksi simetri
        MapInfo[] nearby = rc.senseNearbyMapInfos();
        for (int i = 0; i < nearby.length && Clock.getBytecodesLeft() > 1500; i++) {
            if (!nearby[i].getPaint().isEnemy()) continue;
            MapLocation enemyPaint = nearby[i].getMapLocation();

            // Coba cocokkan dengan prediksi rotasi
            if (SharedMemory.predRotational != null
                    && enemyPaint.distanceSquaredTo(SharedMemory.predRotational) <= 50) {
                SharedMemory.confirmSymmetry(1);
                return;
            }
            if (SharedMemory.predReflectX != null
                    && enemyPaint.distanceSquaredTo(SharedMemory.predReflectX) <= 50) {
                SharedMemory.confirmSymmetry(2);
                return;
            }
            if (SharedMemory.predReflectY != null
                    && enemyPaint.distanceSquaredTo(SharedMemory.predReflectY) <= 50) {
                SharedMemory.confirmSymmetry(3);
                return;
            }
        }
    }

    /*
     Mesin keputusan greedy: menentukan state prioritas tertinggi yang bisa dilakukan.
     Urutan prioritas: scout > refill > selesaikan tower > bangun tower
     > cat musuh > cat kosong > jelajah.
     */
    private void decide() throws GameActionException {
        // Mode scout: 3 soldier pertama menuju prediksi simetri masing-masing
        if (scoutType > 0 && !scoutDone) {
            MapLocation scoutTarget = getScoutTarget();
            if (scoutTarget != null && rc.getLocation().distanceSquaredTo(scoutTarget) > 16) {
                state = State.SCOUT_ENEMY;
                return;
            } else {
                scoutDone = true;
            }
        }

        // REFILL
        if (isPaintLow() || isRefilling) {
            state = State.REFILL;
            return;
        }

        // COMPLETE TOWER: selesaikan pembangunan tower jika sudah siap
        if (rc.isActionReady() && Clock.getBytecodesLeft() > 3000 && canCompleteAnyTower()) {
            state = State.COMPLETE_TOWER;
            return;
        }

        // BUILD TOWER: klaim ruin jika ada
        MapLocation ruin = findBestRuin();
        if (ruin != null) {
            targetRuin = ruin;
            state = State.BUILD_TOWER;
            return;
        }

        // PAINT ENEMY: reclaim tile musuh (nilai 2x)
        if (rc.isActionReady() && Clock.getBytecodesLeft() > 2000 && hasEnemyPaintInRange()) {
            state = State.PAINT_ENEMY;
            return;
        }

        // PAINT EMPTY
        if (rc.isActionReady() && Clock.getBytecodesLeft() > 2000 && hasEmptyPaintInRange()) {
            state = State.PAINT_EMPTY;
            return;
        }

        state = State.EXPLORE;
    }

    //Kembalikan target simetri sesuai jenis penugasan scout robot ini
    private MapLocation getScoutTarget() {
        switch (scoutType) {
            case 1: return SharedMemory.predRotational;
            case 2: return SharedMemory.predReflectX;
            case 3: return SharedMemory.predReflectY;
            default: return SharedMemory.predictedEnemyTower;
        }
    }

    //Jalankan aksi sesuai state yang telah ditetapkan oleh decide()
    private void execute() throws GameActionException {
        switch (state) {
            case REFILL:         doRefill();        break;
            case COMPLETE_TOWER: doCompleteTower(); break;
            case BUILD_TOWER:    doBuildTower();    break;
            case PAINT_ENEMY:    doPaintEnemy();    break;
            case PAINT_EMPTY:    doPaintEmpty();    break;
            case EXPLORE:        doExplore();       break;
            case SCOUT_ENEMY:    doScoutEnemy();    break;
        }
    }

    // Delegasi ke handleRefill() di BaseRobot
    private void doRefill() throws GameActionException { handleRefill(); }

    /** Cek apakah ada ruin di sekitar yang siap diselesaikan menjadi tower. */
    private boolean canCompleteAnyTower() throws GameActionException {
        if (Clock.getBytecodesLeft() < 2000) return false;
        MapInfo[] nearby = rc.senseNearbyMapInfos();
        for (int i = 0; i < nearby.length && Clock.getBytecodesLeft() > 1500; i++) {
            if (!nearby[i].hasRuin()) continue;
            MapLocation loc = nearby[i].getMapLocation();
            if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, loc)) return true;
            if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_MONEY_TOWER, loc)) return true;
        }
        return false;
    }

    //Selesaikan semua tower yang bisa dibangun di dekat posisi sekarang
    private void doCompleteTower() throws GameActionException {
        if (!rc.isActionReady()) return;
        MapInfo[] nearby = rc.senseNearbyMapInfos();
        for (int i = 0; i < nearby.length && Clock.getBytecodesLeft() > 1500; i++) {
            if (!nearby[i].hasRuin()) continue;
            MapLocation loc = nearby[i].getMapLocation();
            if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, loc)) {
                rc.completeTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, loc);
                SharedMemory.removeRuin(loc);
                SharedMemory.exploreTarget = null;
                return;
            }
            if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_MONEY_TOWER, loc)) {
                rc.completeTowerPattern(UnitType.LEVEL_ONE_MONEY_TOWER, loc);
                SharedMemory.removeRuin(loc);
                SharedMemory.exploreTarget = null;
                return;
            }
        }
    }

    /*
     Pilih ruin terbaik untuk dibangun menggunakan skor:
     correctCount * 10 - jarak / 5
     Ruin yang paling banyak progress dan paling dekat diprioritaskan.
     */
    private MapLocation findBestRuin() throws GameActionException {
        if (Clock.getBytecodesLeft() < 3000) return null;
        MapInfo[] nearby = rc.senseNearbyMapInfos();
        MapLocation bestRuin = null;
        int bestScore = -1;

        for (int i = 0; i < nearby.length && Clock.getBytecodesLeft() > 1500; i++) {
            if (!nearby[i].hasRuin()) continue;
            MapLocation ruinLoc = nearby[i].getMapLocation();
            try {
                if (rc.senseRobotAtLocation(ruinLoc) != null) continue;
            } catch (GameActionException e) { continue; }

            // Hitung progress pattern (berapa tile sudah benar)
            int correctCount = countCorrectPatternTiles(ruinLoc);
            int dist = rc.getLocation().distanceSquaredTo(ruinLoc);
            int score = correctCount * 10 - dist / 5;
            if (score > bestScore) { bestScore = score; bestRuin = ruinLoc; }
        }

        // Fallback: gunakan ruin dari memori jika tidak ada yang terlihat
        if (bestRuin == null && SharedMemory.ruinCount > 0) {
            bestRuin = SharedMemory.nearestRuin(rc.getLocation());
        }
        return bestRuin;
    }

    //Hitung jumlah tile dalam area ruin yang sudah sesuai pola yang diinginkan
    private int countCorrectPatternTiles(MapLocation ruinLoc) throws GameActionException {
        int count = 0;
        try {
            MapInfo[] patternArea = rc.senseNearbyMapInfos(ruinLoc, 8);
            for (int i = 0; i < patternArea.length && Clock.getBytecodesLeft() > 1000; i++) {
                MapInfo t = patternArea[i];
                if (t.getMark() != PaintType.EMPTY && t.getMark() == t.getPaint()) count++;
            }
        } catch (Exception e) { /* ignore */ }
        return count;
    }

    //Laksanakan proses pembangunan tower: cat tile pola, tandai, atau selesaikan. 
    private void doBuildTower() throws GameActionException {
        if (targetRuin == null) { state = State.EXPLORE; return; }

        if (rc.canSenseLocation(targetRuin)) {
            try {
                RobotInfo ri = rc.senseRobotAtLocation(targetRuin);
                if (ri != null) {
                    SharedMemory.removeRuin(targetRuin);
                    if (ri.getTeam() == rc.getTeam()) SharedMemory.addAlliedTower(targetRuin);
                    targetRuin = null;
                    state = State.EXPLORE;
                    return;
                }
            } catch (GameActionException e) {} //di skip
        }

        // Kembali isi cat jika terlalu sedikit untuk menyelesaikan pola
        if (rc.getPaint() < 30) {
            state = State.REFILL;
            return;
        }

        // Complete jika bisa
        if (rc.isActionReady()) {
            if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, targetRuin)) {
                rc.completeTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, targetRuin);
                SharedMemory.removeRuin(targetRuin); SharedMemory.exploreTarget = null;
                targetRuin = null; return;
            }
            if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_MONEY_TOWER, targetRuin)) {
                rc.completeTowerPattern(UnitType.LEVEL_ONE_MONEY_TOWER, targetRuin);
                SharedMemory.removeRuin(targetRuin); SharedMemory.exploreTarget = null;
                targetRuin = null; return;
            }
        }

        // Tandai pola tower jika belum ditandai
        if (rc.isActionReady() && rc.canSenseLocation(targetRuin)) {
            try {
                if (rc.senseMapInfo(targetRuin).getMark() == PaintType.EMPTY) {
                    if (rc.canMarkTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, targetRuin))
                        rc.markTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, targetRuin);
                }
            } catch (GameActionException e) {} //di skip
        }

        // Cat tile pola yang belum sesuai
        if (rc.isActionReady() && Clock.getBytecodesLeft() > 1500) {
            boolean painted = paintPatternTile(targetRuin);
            if (!painted && rc.isMovementReady()) moveTo(targetRuin);
        } else if (rc.isMovementReady()) {
            moveTo(targetRuin);
        }
    }

    // Cat satu tile pola yang belum sesuai di sekitar ruin yang dituju
    private boolean paintPatternTile(MapLocation ruinLoc) throws GameActionException {
        if (Clock.getBytecodesLeft() < 1500) return false;
        MapInfo[] patternArea = rc.senseNearbyMapInfos(ruinLoc, 8);
        for (int i = 0; i < patternArea.length && Clock.getBytecodesLeft() > 1000; i++) {
            MapInfo t = patternArea[i];
            if (t.getMark() == PaintType.EMPTY) continue;
            if (t.getMark() == t.getPaint()) continue;
            MapLocation tLoc = t.getMapLocation();
            boolean useSecondary = (t.getMark() == PaintType.ALLY_SECONDARY);
            if (rc.canAttack(tLoc)) {
                rc.attack(tLoc, useSecondary);
                return true;
            }
        }
        return false;
    }

    // Cek apakah ada tile musuh dalam jangkauan aksi
    private boolean hasEnemyPaintInRange() throws GameActionException {
        if (Clock.getBytecodesLeft() < 1000) return false;
        MapInfo[] nearby = rc.senseNearbyMapInfos(rc.getType().actionRadiusSquared);
        for (MapInfo tile : nearby) {
            if (tile.getPaint().isEnemy() && rc.canAttack(tile.getMapLocation())) return true;
        }
        return false;
    }

    // Serang tile musuh terbaik, lalu bergerak untuk melanjutkan pengecatan
    private void doPaintEnemy() throws GameActionException {
        if (rc.isActionReady()) {
            MapLocation target = findBestPaintTarget(true);
            if (target != null) rc.attack(target);
        }
        if (rc.isMovementReady()) moveForPaint();
    }

    // Cek apakah ada tile kosong (belum dicat sekutu) dalam jangkauan aksi
    private boolean hasEmptyPaintInRange() throws GameActionException {
        if (Clock.getBytecodesLeft() < 1000) return false;
        MapInfo[] nearby = rc.senseNearbyMapInfos(rc.getType().actionRadiusSquared);
        for (MapInfo tile : nearby) {
            PaintType p = tile.getPaint();
            if (!p.isAlly() && rc.canAttack(tile.getMapLocation())) return true;
        }
        return false;
    }

    // Serang tile kosong terbaik, lalu bergerak untuk melanjutkan pengecatan.
    private void doPaintEmpty() throws GameActionException {
        if (rc.isActionReady()) {
            MapLocation target = findBestPaintTarget(false);
            if (target != null) rc.attack(target);
        }
        if (rc.isMovementReady()) moveForPaint();
    }

    /*
     Pilih target cat terbaik dengan skor greedy:
     tile musuh +100, tile kosong +40, bonus +60 jika dekat ruin.
     */
    private MapLocation findBestPaintTarget(boolean preferEnemy) throws GameActionException {
        if (Clock.getBytecodesLeft() < 1500) return null;
        MapInfo[] candidates = rc.senseNearbyMapInfos(rc.getType().actionRadiusSquared);
        MapLocation bestLoc = null;
        int bestScore = Integer.MIN_VALUE;

        for (int i = 0; i < candidates.length && Clock.getBytecodesLeft() > 1000; i++) {
            MapInfo tile = candidates[i];
            MapLocation loc = tile.getMapLocation();
            if (!rc.canAttack(loc)) continue;
            if (tile.isWall() || tile.hasRuin()) continue;
            PaintType paint = tile.getPaint();
            if (paint.isAlly()) continue;

            int score = paint.isEnemy() ? 100 : 40;

            // Bonus jika tile dekat ruin yang diingat
            if (SharedMemory.ruinCount > 0) {
                MapLocation nearRuin = SharedMemory.nearestRuin(loc);
                if (nearRuin != null && loc.distanceSquaredTo(nearRuin) <= 8) score += 60;
            }

            if (score > bestScore) { bestScore = score; bestLoc = loc; }
        }
        return bestLoc;
    }

    /*
     Pilih arah gerak terbaik saat mode pengecatan.
     Gunakan skor per tile (hemat bytecode) tanpa scan area penuh tiap arah.
     */
    private void moveForPaint() throws GameActionException {
        if (!rc.isMovementReady() || Clock.getBytecodesLeft() < 2000) return;

        Direction bestDir = null;
        int bestScore = Integer.MIN_VALUE;

        for (Direction dir : DIRS) {
            if (!rc.canMove(dir)) continue;
            MapLocation next = rc.getLocation().add(dir);
            int score = calcMovePaintScore(next);
            if (score > bestScore) { bestScore = score; bestDir = dir; }
        }

        if (bestDir != null) {
            rc.move(bestDir);
            SharedMemory.recordTrail(rc.getLocation());
        }
    }

    // Hitung skor tile sebagai tujuan gerak saat mode pengecatan
    private int calcMovePaintScore(MapLocation loc) throws GameActionException {
        if (Clock.getBytecodesLeft() < 1200) return Integer.MIN_VALUE;
        if (!rc.canSenseLocation(loc)) return 0;

        MapInfo info = rc.senseMapInfo(loc);
        if (info.isWall() || info.hasRuin()) return Integer.MIN_VALUE;

        int score = 0;
        PaintType p = info.getPaint();
        if (p.isEnemy())      score += 80;
        else if (!p.isAlly()) score += 30;
        else                  score -= 15;

        // Bonus jika tile dekat ruin (tidak scan area penuh — hemat bytecode)
        if (SharedMemory.ruinCount > 0) {
            MapLocation ruin = SharedMemory.nearestRuin(loc);
            if (ruin != null) {
                int distToRuin = loc.distanceSquaredTo(ruin);
                if (distToRuin <= 16) score += 80 - distToRuin * 3;
            }
        }

        // Bonus semakin jauh dari tower sekutu (area dekat tower biasanya sudah dicat)
        if (SharedMemory.alliedTowerCount > 0) {
            MapLocation at = SharedMemory.nearestAlliedTower(loc);
            if (at != null) score += Math.min(at.distanceSquaredTo(loc) / 2, 30);
        }

        score -= SharedMemory.trailPenalty(loc);
        return score;
    }

    // Jelajah peta sambil mengecat tile jika tersedia
    private void doExplore() throws GameActionException {
        if (rc.isActionReady() && Clock.getBytecodesLeft() > 3000) {
            MapLocation paintTarget = findBestPaintTarget(true);
            if (paintTarget == null) paintTarget = findBestPaintTarget(false);
            if (paintTarget != null) rc.attack(paintTarget);
        }
        if (rc.isMovementReady()) explore();
    }

    // Bergerak menuju prediksi lokasi musuh sambil tetap mengecat tile di sepanjang jalan.
    private void doScoutEnemy() throws GameActionException {
        // Tetap mengecat selama perjalanan menuju target scout
        if (rc.isActionReady() && Clock.getBytecodesLeft() > 3000) {
            MapLocation paintTarget = findBestPaintTarget(true);
            if (paintTarget == null) paintTarget = findBestPaintTarget(false);
            if (paintTarget != null) rc.attack(paintTarget);
        }
        if (rc.isMovementReady()) {
            MapLocation scoutTarget = getScoutTarget();
            if (scoutTarget != null) moveTo(scoutTarget);
            else explore();
        }
    }
}