package altbot2;

import battlecode.common.*;

/*
 Unit yang mengcat tile secara area (AoE). Bergerak ke arah area padat
 dan menyerang untuk memaksimalkan jumlah tile yang dicat sekaligus.
 Jika lokasi tower musuh diketahui, bergerak ke arahnya agar lebih agresif.
 */
public class SplasherUnit extends BaseRobot {

    private enum State { REFILL, SPLASH_ATTACK, SEEK_DENSITY, RESOURCE_PATTERN }
    private State state = State.SEEK_DENSITY;

    private static final int MIN_SPLASH_VALUE = 3;

    public SplasherUnit(RobotController rc) {
        super(rc);
    }

    @Override
    protected void tick() throws GameActionException {
        sense();
        readMessages();

        if (Clock.getBytecodesLeft() > 5000) decide();
        if (Clock.getBytecodesLeft() > 3000) execute();
    }

    //Mesin keputusan: tentukan state prioritas tertinggi yang bisa dilakukan
    private void decide() throws GameActionException {
        if (isPaintLow() || isRefilling) {
            state = State.REFILL;
            return;
        }

        // Resource pattern memberikan +200 chips, sangat berharga
        if (Clock.getBytecodesLeft() > 4000 && canCompleteResourcePattern()) {
            state = State.RESOURCE_PATTERN;
            return;
        }

        // Splash jika ada target
        if (Clock.getBytecodesLeft() > 4000 && findBestSplashTarget() != null) {
            state = State.SPLASH_ATTACK;
            return;
        }

        state = State.SEEK_DENSITY;
    }

    // Jalankan aksi sesuai state yang telah ditetapkan oleh decide()
    private void execute() throws GameActionException {
        switch (state) {
            case REFILL:           handleRefill();      break;
            case SPLASH_ATTACK:    doSplashAttack();    break;
            case SEEK_DENSITY:     doSeekDensity();     break;
            case RESOURCE_PATTERN: doResourcePattern(); break;
        }
    }

    // Serang target splash terbaik, lalu bergerak ke area tile padat
    private void doSplashAttack() throws GameActionException {
        if (rc.isActionReady() && Clock.getBytecodesLeft() > 3000) {
            MapLocation best = findBestSplashTarget();
            if (best != null) rc.attack(best);
        }
        if (rc.isMovementReady() && Clock.getBytecodesLeft() > 2000) moveToHighDensity();
    }

    /*
     Pilih target splash terbaik dengan skor: tile musuh +3, tile kosong +1
     Target dipilih hanya jika skor mencapai ambang minimum (MIN_SPLASH_VALUE = 3)
     */
    private MapLocation findBestSplashTarget() throws GameActionException {
        if (Clock.getBytecodesLeft() < 3000) return null;
        MapInfo[] candidates = rc.senseNearbyMapInfos(rc.getType().actionRadiusSquared);
        MapLocation bestTarget = null;
        int bestScore = MIN_SPLASH_VALUE;

        for (int i = 0; i < candidates.length && Clock.getBytecodesLeft() > 2000; i++) {
            MapInfo candidate = candidates[i];
            if (candidate.isWall() || candidate.hasRuin()) continue;
            MapLocation targetLoc = candidate.getMapLocation();
            if (!rc.canAttack(targetLoc)) continue;

            int score = calcSplashScore(targetLoc);
            if (score > bestScore) { bestScore = score; bestTarget = targetLoc; }
        }
        return bestTarget;
    }

    // Hitung skor splash untuk suatu lokasi berdasarkan tile di area dampaknya
    private int calcSplashScore(MapLocation loc) throws GameActionException {
        if (Clock.getBytecodesLeft() < 1500) return 0;
        int score = 0;
        MapInfo[] splashArea = rc.senseNearbyMapInfos(loc, 4);
        for (int i = 0; i < splashArea.length && Clock.getBytecodesLeft() > 1000; i++) {
            MapInfo t = splashArea[i];
            if (t.isWall() || t.hasRuin()) continue;
            if (t.getPaint().isEnemy())      score += 3;
            else if (!t.getPaint().isAlly()) score += 1;
        }
        return score;
    }

    // Cari dan serang target splash; bergerak ke area tile terpadat.
    private void doSeekDensity() throws GameActionException {
        if (rc.isActionReady() && Clock.getBytecodesLeft() > 3000) {
            MapLocation best = findBestSplashTarget();
            if (best != null) rc.attack(best);
        }
        if (rc.isMovementReady() && Clock.getBytecodesLeft() > 2000) moveToHighDensity();
    }

    // Bergerak ke target dengan kepadatan tile yang belum dicat paling tinggi
    private void moveToHighDensity() throws GameActionException {
        if (!rc.isMovementReady() || Clock.getBytecodesLeft() < 2000) return;

        if (SharedMemory.exploreTarget == null
                || rc.getLocation().distanceSquaredTo(SharedMemory.exploreTarget) <= 9) {
            SharedMemory.exploreTarget = findDensityTarget();
        }

        if (SharedMemory.exploreTarget != null) moveTo(SharedMemory.exploreTarget);
        else explore();
    }

    /*
     Tentukan target pergerakan berdasarkan kepadatan tile.
     Jika tower musuh diketahui, bergerak ke titik tengah antara posisi
     kita dan tower musuh (agresif). Jika tidak, gunakan heuristik kuadran.
     */
    private MapLocation findDensityTarget() throws GameActionException {
        if (Clock.getBytecodesLeft() < 2000) return pickExploreTarget();

        // Prioritas: arahkan ke titik tengah antara posisi ini dan tower musuh
        if (SharedMemory.enemyTowerCount > 0) {
            MapLocation enemyTower = SharedMemory.enemyTowers[0];
            // Berhenti di setengah jarak agar tidak terlalu dekat dengan tower musuh
            int midX = (rc.getLocation().x + enemyTower.x) / 2;
            int midY = (rc.getLocation().y + enemyTower.y) / 2;
            midX = Math.max(0, Math.min(SharedMemory.mapWidth  - 1, midX));
            midY = Math.max(0, Math.min(SharedMemory.mapHeight - 1, midY));
            return new MapLocation(midX, midY);
        }

        // Fallback: heuristik kuadran dari tile yang terlihat
        if (Clock.getBytecodesLeft() < 3000) return pickExploreTarget();

        MapInfo[] allVisible = rc.senseNearbyMapInfos();
        MapLocation me = rc.getLocation();
        int[] qScore = new int[4];
        int[] qCount = new int[4];

        for (int i = 0; i < allVisible.length && Clock.getBytecodesLeft() > 2000; i++) {
            MapInfo tile = allVisible[i];
            if (tile.isWall() || tile.hasRuin()) continue;
            MapLocation loc = tile.getMapLocation();
            int dx = loc.x - me.x, dy = loc.y - me.y;
            int q = (dx >= 0 ? 0 : 1) + (dy < 0 ? 2 : 0);
            qCount[q]++;
            if (!tile.getPaint().isAlly())
                qScore[q] += tile.getPaint().isEnemy() ? 3 : 1;
        }

        int bestQ = 0; double bestDensity = -1;
        for (int i = 0; i < 4; i++) {
            if (qCount[i] == 0) continue;
            double density = (double) qScore[i] / qCount[i];
            if (density > bestDensity) { bestDensity = density; bestQ = i; }
        }

        // Bergerak ke kuadran dengan kepadatan tile belum dicat tertinggi
        if (bestDensity > 0.25) {
            int offset = 10;
            int tx = me.x + (bestQ % 2 == 0 ? offset : -offset);
            int ty = me.y + (bestQ < 2 ? offset : -offset);
            tx = Math.max(0, Math.min(SharedMemory.mapWidth  - 1, tx));
            ty = Math.max(0, Math.min(SharedMemory.mapHeight - 1, ty));
            return new MapLocation(tx, ty);
        }

        return pickExploreTarget();
    }

    // Cek apakah ada resource pattern yang bisa diselesaikan di sekita
    private boolean canCompleteResourcePattern() throws GameActionException {
        if (Clock.getBytecodesLeft() < 2000) return false;
        MapInfo[] nearby = rc.senseNearbyMapInfos();
        for (int i = 0; i < nearby.length && Clock.getBytecodesLeft() > 1500; i++) {
            MapInfo tile = nearby[i];
            if (!tile.hasRuin() && !tile.isWall()) {
                if (rc.canCompleteResourcePattern(tile.getMapLocation())) return true;
            }
        }
        return false;
    }

    // Selesaikan atau tandai resource pattern untuk mendapatkan bonus chip
    private void doResourcePattern() throws GameActionException {
        if (Clock.getBytecodesLeft() < 2000) return;
        MapInfo[] nearby = rc.senseNearbyMapInfos();
        for (int i = 0; i < nearby.length && Clock.getBytecodesLeft() > 1500; i++) {
            MapInfo tile = nearby[i];
            if (tile.hasRuin() || tile.isWall()) continue;
            MapLocation loc = tile.getMapLocation();
            if (rc.canCompleteResourcePattern(loc)) { rc.completeResourcePattern(loc); return; }
            if (rc.canMarkResourcePattern(loc))     { rc.markResourcePattern(loc);     return; }
        }
        state = State.SEEK_DENSITY;
    }
}