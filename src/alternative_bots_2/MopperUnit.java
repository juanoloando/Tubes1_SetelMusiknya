package alternative_bots_2;

import battlecode.common.*;

/**
 * Unit pertempuran yang menyerang musuh, membersihkan cat musuh,
 * dan mencuri cat dari tile musuh untuk mengisi ulang sendiri tanpa perlu ke tower.
 */
public class MopperUnit extends BaseRobot {

    private enum State { REFILL, MOP_SWING, STEAL_PAINT, CLEAN_ENEMY, SUPPORT_ALLY, PURSUE_TOWER, PATROL }
    private State state = State.PATROL;

    // Ambang refill lebih rendah karena mopper bisa mencuri cat dari tile musuh
    private static final float MOPPER_REFILL = 0.15f;

    public MopperUnit(RobotController rc) {
        super(rc);
    }

    @Override
    protected void tick() throws GameActionException {
        sense();
        readMessages();

        // Laporkan semua tower musuh yang diketahui ke jaringan
        if (Clock.getBytecodesLeft() > 3000) reportAllEnemyTowers();

        if (Clock.getBytecodesLeft() > 5000) decide();
        if (Clock.getBytecodesLeft() > 3000) execute();
    }

    // Kirimkan lokasi semua tower musuh yang diketahui ke tower sekutu terdekat
    private void reportAllEnemyTowers() throws GameActionException {
        if (SharedMemory.enemyTowerCount == 0) return;
        MapLocation tower = SharedMemory.nearestAlliedTower(rc.getLocation());
        if (tower == null) return;

        // Kirim semua entri tower musuh dengan batas bytecode
        for (int i = 0; i < SharedMemory.enemyTowerCount && Clock.getBytecodesLeft() > 1500; i++) {
            MapLocation enemy = SharedMemory.enemyTowers[i];
            if (rc.canSendMessage(tower)) {
                int encoded = 0x80000000 | (enemy.x << 16) | enemy.y;
                rc.sendMessage(tower, encoded);
            }
        }
    }

    // Mesin keputusan greedy: tentukan state prioritas tertinggi yang bisa dilakukan. 
    private void decide() throws GameActionException {
        // Refill jika stok cat terlalu rendah (ambang lebih rendah karena bisa mencuri)
        if (rc.getPaint() < (int)(rc.getType().paintCapacity * MOPPER_REFILL)) {
            state = State.REFILL;
            return;
        }
        if (isRefilling && isPaintFull()) {
            isRefilling = false;
        } else if (isRefilling) {
            state = State.REFILL;
            return;
        }

        // Mop swing: jika ada banyak musuh di sekitar, ayunkan mop
        if (rc.isActionReady() && Clock.getBytecodesLeft() > 4000) {
            if (findBestMopSwingDir() != null) {
                state = State.MOP_SWING;
                return;
            }
        }

        // Curi cat musuh: isi ulang gratis jika stok masih rendah
        if (rc.isActionReady()
                && rc.getPaint() < (int)(rc.getType().paintCapacity * 0.6f)
                && Clock.getBytecodesLeft() > 3000) {
            if (hasEnemyPaintInRange()) {
                state = State.STEAL_PAINT;
                return;
            }
        }

        // Bantu sekutu yang stok catnya kritis
        if (Clock.getBytecodesLeft() > 2500 && hasAllyToSupport()) {
            state = State.SUPPORT_ALLY;
            return;
        }

        // Bersihkan cat musuh di sekitar
        if (Clock.getBytecodesLeft() > 2500 && hasEnemyPaintNearby()) {
            state = State.CLEAN_ENEMY;
            return;
        }

        // Kejar tower musuh jika lokasinya diketahui
        if (SharedMemory.enemyTowerCount > 0) {
            state = State.PURSUE_TOWER;
            return;
        }

        state = State.PATROL;
    }

    // Jalankan aksi sesuai state yang telah ditetapkan oleh decide(). 
    private void execute() throws GameActionException {
        switch (state) {
            case REFILL:       doMopperRefill(); break;
            case MOP_SWING:    doMopSwing();     break;
            case STEAL_PAINT:  doStealPaint();   break;
            case CLEAN_ENEMY:  doCleanEnemy();   break;
            case SUPPORT_ALLY: doSupportAlly();  break;
            case PURSUE_TOWER: doPursueTower();  break;
            case PATROL:       doPatrol();       break;
        }
    }

    // Coba curi cat musuh terlebih dahulu sebelum pergi ke tower untuk refill.
    private void doMopperRefill() throws GameActionException {
        if (rc.isActionReady() && Clock.getBytecodesLeft() > 2000) {
            MapLocation stealTarget = findBestEnemyPaintTarget();
            if (stealTarget != null) {
                rc.attack(stealTarget);
                return;
            }
        }
        handleRefill();
    }

    // Tentukan arah ayunan mop yang mengenai paling banyak unit musuh. 
    private Direction findBestMopSwingDir() throws GameActionException {
        if (Clock.getBytecodesLeft() < 2000) return null;
        Direction bestDir = null;
        int bestHits = 0;
        for (Direction dir : DIRS) {
            if (!rc.canMopSwing(dir)) continue;
            int hits = countMopSwingHits(dir);
            if (hits > bestHits) { bestHits = hits; bestDir = dir; }
        }
        return bestHits > 0 ? bestDir : null;
    }

    // Lakukan ayunan mop ke arah terbaik, lalu kejar musuh terdekat.
    private void doMopSwing() throws GameActionException {
        if (rc.isActionReady() && Clock.getBytecodesLeft() > 2000) {
            Direction bestDir = findBestMopSwingDir();
            if (bestDir != null) rc.mopSwing(bestDir);
        }
        if (rc.isMovementReady() && Clock.getBytecodesLeft() > 1500) chaseEnemy();
    }

    // Hitung jumlah musuh yang akan terkena di area ayunan mop arah tertentu. 
    private int countMopSwingHits(Direction dir) throws GameActionException {
        MapLocation me = rc.getLocation();
        MapLocation[] swingTiles = {
            me.add(dir).add(dir.rotateLeft()),
            me.add(dir),
            me.add(dir).add(dir.rotateRight())
        };
        int hits = 0;
        for (MapLocation tile : swingTiles) {
            if (!rc.onTheMap(tile)) continue;
            try {
                if (!rc.canSenseLocation(tile)) continue;
                RobotInfo robot = rc.senseRobotAtLocation(tile);
                if (robot != null && robot.getTeam() != rc.getTeam()) hits++;
            } catch (GameActionException e) { /* lewati */ }
        }
        return hits;
    }

    //steal paint dari tile musuh terdekat jika stok cat masih rendah, lalu refill jika perlu

    private boolean hasEnemyPaintInRange() throws GameActionException {
        if (Clock.getBytecodesLeft() < 1000) return false;
        MapInfo[] nearby = rc.senseNearbyMapInfos(rc.getType().actionRadiusSquared);
        for (MapInfo tile : nearby) {
            if (tile.getPaint().isEnemy() && rc.canAttack(tile.getMapLocation())) return true;
        }
        return false;
    }

    private void doStealPaint() throws GameActionException {
        if (rc.isActionReady() && Clock.getBytecodesLeft() > 2000) {
            MapLocation target = findBestEnemyPaintTarget();
            if (target != null) rc.attack(target);
        }
        if (rc.isMovementReady() && Clock.getBytecodesLeft() > 1500) seekEnemyPaint();
    }

    /**
     * Pilih target cat musuh terbaik untuk diserang:
     * prioritas tile yang dekat tower sekutu dan punya banyak tetangga musuh.
     * Menggunakan 4 arah kardinal agar lebih hemat bytecode.
     */
    private MapLocation findBestEnemyPaintTarget() throws GameActionException {
        if (Clock.getBytecodesLeft() < 1500) return null;
        MapInfo[] candidates = rc.senseNearbyMapInfos(rc.getType().actionRadiusSquared);
        MapLocation bestTarget = null;
        int bestScore = Integer.MIN_VALUE;
        MapLocation nearestAllied = SharedMemory.nearestAlliedTower(rc.getLocation());

        for (int i = 0; i < candidates.length && Clock.getBytecodesLeft() > 1000; i++) {
            MapInfo tile = candidates[i];
            if (!tile.getPaint().isEnemy()) continue;
            MapLocation loc = tile.getMapLocation();
            if (!rc.canAttack(loc)) continue;

            int score = 0;
            if (nearestAllied != null) {
                int distToBase = loc.distanceSquaredTo(nearestAllied);
                score += Math.max(0, 80 - distToBase);
            }
            // Gunakan 4 arah kardinal saja untuk hemat bytecode
            score += countAdjacentEnemyCard(loc) * 10;

            if (score > bestScore) { bestScore = score; bestTarget = loc; }
        }
        return bestTarget;
    }

    // Hitung jumlah tile musuh yang bersebelahan (4 arah kardinal saja)
    private int countAdjacentEnemyCard(MapLocation loc) throws GameActionException {
        int count = 0;
        for (Direction dir : CARD) {
            MapLocation adj = loc.add(dir);
            try {
                if (rc.canSenseLocation(adj) && rc.senseMapInfo(adj).getPaint().isEnemy()) count++;
            } catch (GameActionException e) { /* lewati */ }
        }
        return count;
    }

    // Cek apakah ada tile musuh dalam jangkauan aksi. 
    private boolean hasEnemyPaintNearby() throws GameActionException {
        if (Clock.getBytecodesLeft() < 1000) return false;
        MapInfo[] nearby = rc.senseNearbyMapInfos(rc.getType().actionRadiusSquared);
        for (MapInfo tile : nearby) {
            if (tile.getPaint().isEnemy()) return true;
        }
        return false;
    }

    // Bersihkan cat musuh terdekat dan bergerak mendekati area cat musuh.
    private void doCleanEnemy() throws GameActionException {
        if (rc.isActionReady() && Clock.getBytecodesLeft() > 2000) {
            MapLocation target = findBestEnemyPaintTarget();
            if (target != null) rc.attack(target);
        }
        if (rc.isMovementReady() && Clock.getBytecodesLeft() > 1500) seekEnemyPaint();
    }

    // Bergerak menuju area yang banyak cat musuh menggunakan skor greedy. 
    private void seekEnemyPaint() throws GameActionException {
        if (!rc.isMovementReady() || Clock.getBytecodesLeft() < 1500) return;
        Direction bestDir = null;
        int bestScore = Integer.MIN_VALUE;

        for (Direction dir : DIRS) {
            if (!rc.canMove(dir)) continue;
            MapLocation next = rc.getLocation().add(dir);
            int score = 0;
            // Cek tile musuh di empat arah sekitar posisi berikutnya (hemat bytecode)
            for (Direction cd : CARD) {
                MapLocation adj = next.add(cd);
                try {
                    if (rc.canSenseLocation(adj) && rc.senseMapInfo(adj).getPaint().isEnemy())
                        score += 10;
                } catch (GameActionException e) { /* lewati */ }
            }
            score -= SharedMemory.trailPenalty(next);
            if (score > bestScore) { bestScore = score; bestDir = dir; }
        }

        if (bestDir != null) {
            rc.move(bestDir);
            SharedMemory.recordTrail(rc.getLocation());
        }
    }

    // Cek apakah ada sekutu non-tower di dekat dengan stok cat kritis (<20%).
    private boolean hasAllyToSupport() throws GameActionException {
        if (rc.getPaint() < 50) return false;
        RobotInfo[] allies = rc.senseNearbyRobots(2, rc.getTeam());
        for (RobotInfo ally : allies) {
            if (!ally.getType().isTowerType()
                    && ally.getPaintAmount() < ally.getType().paintCapacity * 0.2) return true;
        }
        return false;
    }

    // Transfer sebagian cat ke sekutu yang stoknya kritis. 
    private void doSupportAlly() throws GameActionException {
        if (!rc.isActionReady()) return;
        RobotInfo[] allies = rc.senseNearbyRobots(2, rc.getTeam());
        for (RobotInfo ally : allies) {
            if (!ally.getType().isTowerType()
                    && ally.getPaintAmount() < ally.getType().paintCapacity * 0.2) {
                int donation = Math.min(40, rc.getPaint() - 50);
                if (donation > 0 && rc.canTransferPaint(ally.getLocation(), donation)) {
                    rc.transferPaint(ally.getLocation(), donation);
                    return;
                }
            }
        }
    }

    // Bergerak dan menyerang menuju tower musuh yang diketahui.
    private void doPursueTower() throws GameActionException {
        if (rc.isActionReady() && Clock.getBytecodesLeft() > 2000) {
            MapLocation target = findBestEnemyPaintTarget();
            if (target != null) rc.attack(target);
        }
        if (rc.isMovementReady() && SharedMemory.enemyTowerCount > 0) {
            moveTo(SharedMemory.enemyTowers[0]);
        }
    }

    // Patroli: serang cat musuh terdekat dan bergerak mendekati area cat musuh. 
    private void doPatrol() throws GameActionException {
        if (rc.isActionReady() && Clock.getBytecodesLeft() > 2000) {
            MapLocation target = findBestEnemyPaintTarget();
            if (target != null) rc.attack(target);
        }
        if (rc.isMovementReady() && Clock.getBytecodesLeft() > 1500) seekEnemyPaint();
    }

    // Kejar musuh terdekat; jika tidak ada, cari area cat musuh.
    private void chaseEnemy() throws GameActionException {
        if (!rc.isMovementReady() || Clock.getBytecodesLeft() < 1000) return;
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (enemies.length > 0) moveTo(enemies[0].getLocation());
        else seekEnemyPaint();
    }
}