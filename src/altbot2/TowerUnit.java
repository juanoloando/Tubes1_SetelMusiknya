package altbot2;

import battlecode.common.*;

/*
 Tower yang mengelola spawn unit, serangan, dan upgrade.
 Strategi spawn adaptif berdasarkan fase permainan dan kondisi ancaman musuh.
 */
public class TowerUnit extends BaseRobot {

    // Counter spawn per instance agar tidak terbagi antar tower
    private int soldierSpawned  = 0;
    private int splasherSpawned = 0;
    private int mopperSpawned   = 0;

    // Jumlah scout simetri yang sudah dikirim (maksimal 3)
    private int scoutsSent = 0;
    private int lastBroadcastRound = -999;

    public TowerUnit(RobotController rc) {
        super(rc);
        SharedMemory.addAlliedTower(rc.getLocation());
        SharedMemory.computeEnemyPredictions(rc.getLocation(), rc.getMapWidth(), rc.getMapHeight());
    }

    @Override
    protected void tick() throws GameActionException {
        readMessages();

        int round = rc.getRoundNum();

        // Siarkan lokasi tower ke seluruh peta setiap 8 ronde
        if (round - lastBroadcastRound >= 8) {
            broadcastLocation();
            lastBroadcastRound = round;
        }

        // Serang musuh dengan focus fire (HP terendah pertama)
        if (Clock.getBytecodesLeft() > 4000) tryAttack();

        // Upgrade tower jika aman dan chip mencukupi
        if (Clock.getBytecodesLeft() > 3000) tryUpgrade();

        // Spawn unit baru
        if (Clock.getBytecodesLeft() > 2500) trySpawn();
    }

    // Siarkan lokasi tower ini ke seluruh unit yang bisa menerima pesan
    private void broadcastLocation() throws GameActionException {
        if (!rc.canBroadcastMessage()) return;
        MapLocation loc = rc.getLocation();
        rc.broadcastMessage((loc.x << 16) | loc.y);
    }

    // Serang musuh terdekat yang bisa dijangkau, utamakan yang HP-nya paling rendah. 
    private void tryAttack() throws GameActionException {
        if (!rc.isActionReady()) return;
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (enemies.length == 0) return;

        RobotInfo bestTarget = null;
        int lowestEHP = Integer.MAX_VALUE;

        for (RobotInfo e : enemies) {
            if (!rc.canAttack(e.getLocation())) continue;
            // Tower musuh diberi bobot HP setengahnya agar diprioritaskan untuk dihancurkan
            int ehp = e.getType().isTowerType() ? e.getHealth() / 2 : e.getHealth();
            if (ehp < lowestEHP) { lowestEHP = ehp; bestTarget = e; }
        }

        if (bestTarget != null) rc.attack(bestTarget.getLocation());
    }

    /*
     Upgrade tower jika tidak ada musuh di sekitar dan chip mencukupi.
     Setiap tipe tower punya syarat ronde dan chip yang berbeda.
     */
    private void tryUpgrade() throws GameActionException {
        int chips = rc.getMoney();
        int round = rc.getRoundNum();
        UnitType myType = rc.getType();
        MapLocation myLoc = rc.getLocation();

        // Jangan upgrade jika ada musuh di radius 3 tile
        if (rc.senseNearbyRobots(9, rc.getTeam().opponent()).length > 0) return;

        if (myType == UnitType.LEVEL_ONE_PAINT_TOWER  && round > 250 && chips > 4000) {
            if (rc.canUpgradeTower(myLoc)) rc.upgradeTower(myLoc);
        } else if (myType == UnitType.LEVEL_TWO_PAINT_TOWER && round > 600 && chips > 6000) {
            if (rc.canUpgradeTower(myLoc)) rc.upgradeTower(myLoc);
        } else if (myType == UnitType.LEVEL_ONE_MONEY_TOWER && round > 300 && chips > 3500) {
            if (rc.canUpgradeTower(myLoc)) rc.upgradeTower(myLoc);
        }
    }

    // Spawn unit baru jika cat dan chip mencukupi.
    private void trySpawn() throws GameActionException {
        if (!rc.isActionReady()) return;
        if (rc.getPaint() < 150) return;
        if (rc.getMoney() < 200) return;

        int round = rc.getRoundNum();
        int total = soldierSpawned + splasherSpawned + mopperSpawned;

        UnitType toSpawn = decideSpawn(round, total, rc.getMoney());
        if (toSpawn == null) return;

        MapLocation spawnLoc = findBestSpawnLocation(toSpawn);
        if (spawnLoc == null) return;

        if (rc.canBuildRobot(toSpawn, spawnLoc)) {
            rc.buildRobot(toSpawn, spawnLoc);
            countSpawn(toSpawn);
        }
    }

    /*
    Pohon keputusan greedy untuk menentukan tipe unit yang di-spawn.
     Fase awal (<80): full Soldier untuk klaim ruin lebih cepat.
     Fase tengah (80-400): seimbangkan Soldier, Splasher, dan Mopper.
     Fase akhir (400+): dorong Splasher untuk cat 70% peta.
     */
    private UnitType decideSpawn(int round, int total, int chips) throws GameActionException {
        boolean hasEnemyThreat = SharedMemory.enemyTowerCount > 0
            || rc.senseNearbyRobots(-1, rc.getTeam().opponent()).length > 0;

        // Early game: semua Soldier untuk klaim ruin sesegera mungkin
        if (round < 80) {
            return UnitType.SOLDIER;
        }

        // Ronde 80-120: kirim 3 scout ke prediksi simetri berbeda
        if (round >= 80 && round <= 120 && scoutsSent < 3) {
            // Scout di-spawn sebagai Soldier; SoldierUnit mendeteksi tugasnya
            // berdasarkan urutan kelahiran (soldierSpawned)
            scoutsSent++;
            return UnitType.SOLDIER;
        }

        // Respons ancaman: spawn Mopper jika musuh aktif
        if (hasEnemyThreat) {
            int mopperR = total == 0 ? 0 : mopperSpawned * 100 / total;
            if (mopperR < 25) return UnitType.MOPPER;
        }

        // Mid game: seimbangkan komposisi
        if (round < 400) {
            int soldierR  = total == 0 ? 0 : soldierSpawned  * 100 / total;
            int splasherR = total == 0 ? 0 : splasherSpawned * 100 / total;
            int mopperR   = total == 0 ? 0 : mopperSpawned   * 100 / total;

            if (mopperR < 15)  return UnitType.MOPPER;
            if (soldierR < 40) return UnitType.SOLDIER;
            if (splasherR < 40 && chips >= 400) return UnitType.SPLASHER;
            return UnitType.SOLDIER;
        }

        // Late game: perbanyak Splasher untuk menyelesaikan pengecatan 70% peta
        if (round >= 400) {
            int splasherR = total == 0 ? 0 : splasherSpawned * 100 / total;
            int mopperR   = total == 0 ? 0 : mopperSpawned   * 100 / total;

            if (hasEnemyThreat && mopperR < 20) return UnitType.MOPPER;
            // Splasher AoE lebih efisien untuk mengecat banyak tile sekaligus
            if (splasherR < 50 && chips >= 400) return UnitType.SPLASHER;
            return UnitType.SOLDIER;
        }

        return UnitType.SOLDIER;
    }

    /*
     Pilih lokasi spawn terbaik untuk unit yang akan di-spawn.
     Arahkan ke ruin terdekat (prioritas Soldier), prediksi musuh, atau tile yang belum dicat.
     */
    private MapLocation findBestSpawnLocation(UnitType type) throws GameActionException {
        MapLocation myLoc = rc.getLocation();
        MapLocation bestLoc = null;
        int bestScore = Integer.MIN_VALUE;

        for (Direction dir : DIRS) {
            MapLocation candidate = myLoc.add(dir);
            if (!rc.canBuildRobot(type, candidate)) continue;

            int score = 0;

            // Skor lebih tinggi jika arah spawn mendekat ke ruin
            if (SharedMemory.ruinCount > 0) {
                MapLocation ruin = SharedMemory.nearestRuin(myLoc);
                if (ruin != null) score += 600 - candidate.distanceSquaredTo(ruin);
            }

            // Skor lebih tinggi jika arah spawn mendekat ke prediksi tower musuh
            if (SharedMemory.predictedEnemyTower != null) {
                score += 300 - candidate.distanceSquaredTo(SharedMemory.predictedEnemyTower) / 2;
            }

            // Bonus jika tile spawn belum dicat sekutu
            try {
                MapInfo mi = rc.senseMapInfo(candidate);
                if (!mi.getPaint().isAlly()) score += 50;
            } catch (GameActionException e) { /* lewati */ }

            if (score > bestScore) { bestScore = score; bestLoc = candidate; }
        }
        return bestLoc;
    }

    // Tambahkan hitungan unit yang baru di-spawn sesuai jenisnya.
    private void countSpawn(UnitType type) {
        switch (type) {
            case SOLDIER:  soldierSpawned++;  break;
            case SPLASHER: splasherSpawned++; break;
            case MOPPER:   mopperSpawned++;   break;
            default: break;
        }
    }
}