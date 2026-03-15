package altbot2;

import battlecode.common.*;

// Kelas untuk menyimpan informasi global tentang peta, tower, ruin, dan prediksi musuh yang dapat diakses oleh semua unit.
public class SharedMemory {

    // Dimensi peta
    public static int mapWidth  = 0;
    public static int mapHeight = 0;
    public static boolean mapInfoInitialized = false;

    // Tipe simetri peta: 0=belum diketahui, 1=rotasional, 2=refleksi-X, 3=refleksi-Y
    public static int confirmedSymmetry = 0;
    public static MapLocation predRotational = null;
    public static MapLocation predReflectX   = null;
    public static MapLocation predReflectY   = null;
    public static MapLocation predictedEnemyTower = null;

    // Daftar tower sekutu yang diketahui
    public static MapLocation[] alliedTowers = new MapLocation[20];
    public static int alliedTowerCount = 0;

    // Daftar tower musuh yang diketahui
    public static MapLocation[] enemyTowers = new MapLocation[10];
    public static int enemyTowerCount = 0;

    // Lokasi ruin kosong yang diketahui
    public static MapLocation[] knownRuins = new MapLocation[20];
    public static int ruinCount = 0;

    // Riwayat pergerakan untuk menghindari pengulangan jalur (50 entri)
    public static MapLocation[] trail = new MapLocation[50];
    public static int trailIdx = 0;

    // Target eksplorasi saat ini
    public static MapLocation exploreTarget = null;

    // Jumlah unit yang sudah di-spawn (perkiraan per tower)
    public static int soldierSpawned  = 0;
    public static int splasherSpawned = 0;
    public static int mopperSpawned   = 0;

    // Inisialisasi informasi peta sekali dari konstruktor robot. Dipanggil oleh BaseRobot.
    public static void initMap(RobotController rc) {
        if (!mapInfoInitialized) {
            mapWidth  = rc.getMapWidth();
            mapHeight = rc.getMapHeight();
            mapInfoInitialized = true;
        }
    }

    // Tambahkan tower sekutu ke daftar jika belum ada.
    public static void addAlliedTower(MapLocation loc) {
        for (int i = 0; i < alliedTowerCount; i++) {
            if (alliedTowers[i].equals(loc)) return;
        }
        if (alliedTowerCount < alliedTowers.length) alliedTowers[alliedTowerCount++] = loc;
    }

    // Tambahkan tower musuh ke daftar jika belum ada.
    public static void addEnemyTower(MapLocation loc) {
        for (int i = 0; i < enemyTowerCount; i++) {
            if (enemyTowers[i].equals(loc)) return;
        }
        if (enemyTowerCount < enemyTowers.length) enemyTowers[enemyTowerCount++] = loc;
    }

    // Hapus tower musuh dari daftar (misalnya setelah dihancurkan).
    public static void removeEnemyTower(MapLocation loc) {
        for (int i = 0; i < enemyTowerCount; i++) {
            if (enemyTowers[i].equals(loc)) { enemyTowers[i] = enemyTowers[--enemyTowerCount]; return; }
        }
    }

    // Tambahkan lokasi ruin kosong ke daftar jika belum ada.
    public static void addRuin(MapLocation loc) {
        for (int i = 0; i < ruinCount; i++) {
            if (knownRuins[i].equals(loc)) return;
        }
        if (ruinCount < knownRuins.length) knownRuins[ruinCount++] = loc;
    }

    // Hapus lokasi ruin dari daftar (misalnya setelah dibangun tower).
    public static void removeRuin(MapLocation loc) {
        for (int i = 0; i < ruinCount; i++) {
            if (knownRuins[i].equals(loc)) { knownRuins[i] = knownRuins[--ruinCount]; return; }
        }
    }

    // Kembalikan lokasi tower sekutu terdekat dari posisi tertentu.
    public static MapLocation nearestAlliedTower(MapLocation from) {
        MapLocation best = null; int minDist = Integer.MAX_VALUE;
        for (int i = 0; i < alliedTowerCount; i++) {
            int d = from.distanceSquaredTo(alliedTowers[i]);
            if (d < minDist) { minDist = d; best = alliedTowers[i]; }
        }
        return best;
    }

    // Kembalikan lokasi ruin terdekat dari posisi tertentu.
    public static MapLocation nearestRuin(MapLocation from) {
        MapLocation best = null; int minDist = Integer.MAX_VALUE;
        for (int i = 0; i < ruinCount; i++) {
            int d = from.distanceSquaredTo(knownRuins[i]);
            if (d < minDist) { minDist = d; best = knownRuins[i]; }
        }
        return best;
    }

    // Catat posisi ke riwayat pergerakan untuk menghitung penalti jalur.
    public static void recordTrail(MapLocation loc) {
        trail[trailIdx] = loc;
        trailIdx = (trailIdx + 1) % trail.length;
    }

    // Hitung total penalti jalur untuk suatu lokasi (800 per kunjungan).
    public static int trailPenalty(MapLocation loc) {
        int penalty = 0;
        for (int i = 0; i < trail.length; i++) {
            if (trail[i] != null && trail[i].equals(loc)) penalty += 800;
        }
        return penalty;
    }

    /*
     Hitung ketiga prediksi posisi tower musuh berdasarkan simetri peta.
     Dipanggil sekali dari konstruktor TowerUnit.
     */
    public static void computeEnemyPredictions(MapLocation myLoc, int w, int h) {
        predRotational    = new MapLocation(w - 1 - myLoc.x, h - 1 - myLoc.y);
        predReflectX      = new MapLocation(w - 1 - myLoc.x, myLoc.y);
        predReflectY      = new MapLocation(myLoc.x,          h - 1 - myLoc.y);
        predictedEnemyTower = predRotational;
    }

    /*
     Konfirmasikan jenis simetri peta setelah scout menemukan musuh.
     Memperbarui prediksi tower musuh aktif dan mengabaikan prediksi lainnya.
     */
    public static void confirmSymmetry(int symmetryType) {
        if (confirmedSymmetry != 0) return;
        confirmedSymmetry = symmetryType;
        switch (symmetryType) {
            case 1: predictedEnemyTower = predRotational; break;
            case 2: predictedEnemyTower = predReflectX;   break;
            case 3: predictedEnemyTower = predReflectY;   break;
        }
    }
}