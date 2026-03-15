package altbot2;

import battlecode.common.*;
import java.util.Random;

// Kelas dasar untuk semua unit dan tower. Gunanya buat navigasi, sensing, painting, dan refill. 
public abstract class BaseRobot {

    protected final RobotController rc;
    protected final Random rng;

    protected static final Direction[] DIRS = {
        Direction.NORTH, Direction.NORTHEAST, Direction.EAST, Direction.SOUTHEAST,
        Direction.SOUTH, Direction.SOUTHWEST, Direction.WEST, Direction.NORTHWEST,
    };
    protected static final Direction[] CARD = {
        Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST,
    };

    // Ambang batas cat: di bawah 30% mulai refill, berhenti saat 75%
    protected static final float REFILL_LOW  = 0.30f;
    protected static final float REFILL_FULL = 0.75f;

    // Marker untuk refill per bot agar tidak terbagi antar robot
    protected boolean isRefilling = false;

    public BaseRobot(RobotController rc) {
        this.rc  = rc;
        this.rng = new Random(rc.getID());
        SharedMemory.initMap(rc);
    }

    // Loop utama robot 
    public void run() throws GameActionException {
        while (true) {
            try {
                tick();
            } catch (GameActionException e) {
                System.out.println("[JUANS] GAE: " + e.getMessage());
            } catch (Exception e) {
                System.out.println("[JUANS] EX: " + e.getMessage());
            } finally {
                Clock.yield();
            }
        }
    }

    // logika untuk satu ronde, dipanggil oleh run() buat setiap ronde
    protected abstract void tick() throws GameActionException;

    // Pindai robot dan tile di sekitar untuk memperbarui memori tower dan ruin.
    // Dilindungi guard bytecode agar tidak melebihi batas komputasi.
     
    protected void sense() throws GameActionException {
        if (Clock.getBytecodesLeft() < 2500) return;

        // Pindai semua robot di sekitar: catat tower sekutu dan musuh
        RobotInfo[] nearby = rc.senseNearbyRobots(-1);
        for (int i = 0; i < nearby.length && Clock.getBytecodesLeft() > 2000; i++) {
            RobotInfo r = nearby[i];
            if (!r.getType().isTowerType()) continue;
            if (r.getTeam() == rc.getTeam()) {
                SharedMemory.addAlliedTower(r.getLocation());
            } else {
                SharedMemory.addEnemyTower(r.getLocation());
            }
        }

        // Pindai tile: catat ruin kosong atau ruin yang sudah jadi tower
        if (Clock.getBytecodesLeft() < 3500) return;
        MapInfo[] tiles = rc.senseNearbyMapInfos();
        for (int i = 0; i < tiles.length && Clock.getBytecodesLeft() > 1500; i++) {
            if (!tiles[i].hasRuin()) continue;
            MapLocation ruinLoc = tiles[i].getMapLocation();
            try {
                RobotInfo ri = rc.senseRobotAtLocation(ruinLoc);
                if (ri == null) {
                    SharedMemory.addRuin(ruinLoc);
                } else {
                    SharedMemory.removeRuin(ruinLoc);
                    if (ri.getTeam() == rc.getTeam()) SharedMemory.addAlliedTower(ruinLoc);
                    else SharedMemory.addEnemyTower(ruinLoc);
                }
            } catch (GameActionException e) {}
        }
    }

    /** Navigasi greedy menuju target: pilih arah dengan total jarak + penalti trail terkecil. */
    protected void moveTo(MapLocation target) throws GameActionException {
        if (!rc.isMovementReady()) return;
        if (rc.getLocation().equals(target)) return;

        Direction bestDir = null;
        int bestScore = Integer.MAX_VALUE;

        for (Direction dir : DIRS) {
            if (!rc.canMove(dir)) continue;
            MapLocation next = rc.getLocation().add(dir);
            int total = next.distanceSquaredTo(target) + SharedMemory.trailPenalty(next);
            if (total < bestScore) { bestScore = total; bestDir = dir; }
        }

        if (bestDir != null) {
            rc.move(bestDir);
            SharedMemory.recordTrail(rc.getLocation());
        }
    }

    //Bergerak menuju target eksplorasi; perbarui target jika sudah dekat
    protected void explore() throws GameActionException {
        if (!rc.isMovementReady()) return;

        MapLocation me = rc.getLocation();
        if (SharedMemory.exploreTarget == null
                || me.distanceSquaredTo(SharedMemory.exploreTarget) <= 4) {
            SharedMemory.exploreTarget = pickExploreTarget();
        }

        if (SharedMemory.exploreTarget != null) moveTo(SharedMemory.exploreTarget);
        else randomMove();
    }

    /*
     Pilih target eksplorasi dari 13 kandidat (pojok, tengah, midpoints).
      Pilih kandidat terjauh; jika semua dekat, gunakan posisi acak.
     */
    protected MapLocation pickExploreTarget() {
        int w = SharedMemory.mapWidth;
        int h = SharedMemory.mapHeight;
        MapLocation me = rc.getLocation();

        MapLocation[] candidates = {
            new MapLocation(2, 2),
            new MapLocation(w - 3, 2),
            new MapLocation(2, h - 3),
            new MapLocation(w - 3, h - 3),
            new MapLocation(w / 2, h / 2),
            new MapLocation(w / 4, h / 4),
            new MapLocation(3 * w / 4, h / 4),
            new MapLocation(w / 4, 3 * h / 4),
            new MapLocation(3 * w / 4, 3 * h / 4),
            new MapLocation(w / 2, h / 4),
            new MapLocation(w / 2, 3 * h / 4),
            new MapLocation(w / 4, h / 2),
            new MapLocation(3 * w / 4, h / 2),
        };

        MapLocation best = null;
        int bestDist = -1;
        for (MapLocation c : candidates) {
            if (!rc.onTheMap(c)) continue;
            int dist = me.distanceSquaredTo(c);
            if (dist > bestDist) { bestDist = dist; best = c; }
        }

        if (bestDist < 36) {
            best = new MapLocation(rng.nextInt(SharedMemory.mapWidth), rng.nextInt(SharedMemory.mapHeight));
        }
        return best;
    }

    // Bergerak ke arah acak yang valid.
    protected void randomMove() throws GameActionException {
        if (!rc.isMovementReady()) return;
        Direction d = DIRS[rng.nextInt(DIRS.length)];
        if (rc.canMove(d)) rc.move(d);
    }

    // Cek apakah stok cat di bawah ambang batas untuk mulai refill (30%).
    protected boolean isPaintLow() {
        return rc.getPaint() < (int)(rc.getType().paintCapacity * REFILL_LOW);
    }

    // Cek apakah stok cat sudah cukup penuh untuk berhenti refill (75%).
    protected boolean isPaintFull() {
        return rc.getPaint() >= (int)(rc.getType().paintCapacity * REFILL_FULL);
    }

    /**
     * Tangani proses isi ulang cat ke tower sekutu terdekat.
     * Kembalikan true jika robot sedang dalam mode refill.
     */
    protected boolean handleRefill() throws GameActionException {
        if (!isRefilling && !isPaintLow()) return false;
        if (isRefilling && isPaintFull()) {
            isRefilling = false;
            return false;
        }

        isRefilling = true;
        MapLocation tower = SharedMemory.nearestAlliedTower(rc.getLocation());
        if (tower == null) {
            // Tower belum diketahui: eksplorasi sambil mencari
            isRefilling = false;
            if (rc.isMovementReady()) explore();
            return true;
        }

        int dist = rc.getLocation().distanceSquaredTo(tower);
        if (dist <= 2) {
            if (rc.isActionReady()) {
                int needed = rc.getType().paintCapacity - rc.getPaint();
                if (needed > 0 && rc.canTransferPaint(tower, -needed)) {
                    rc.transferPaint(tower, -needed);
                }
            }
        } else {
            moveTo(tower);
        }
        return true;
    }

    /**
     * Baca pesan masuk dari tower.
     * Bit 31 = 1 berarti tower musuh (enemy); bit 31 = 0 berarti tower sekutu.
     */
    protected void readMessages() throws GameActionException {
        if (Clock.getBytecodesLeft() < 1000) return;
        Message[] msgs = rc.readMessages(-1);
        for (Message m : msgs) {
            int content = m.getBytes();
            if ((content & 0x80000000) != 0) {
                int x = (content >> 16) & 0x7FFF;
                int y = content & 0xFFFF;
                if (x >= 0 && y >= 0 && x < SharedMemory.mapWidth && y < SharedMemory.mapHeight) {
                    SharedMemory.addEnemyTower(new MapLocation(x, y));
                }
            } else {
                int x = (content >> 16) & 0xFFFF;
                int y = content & 0xFFFF;
                if (x > 0 && y > 0 && x < SharedMemory.mapWidth && y < SharedMemory.mapHeight) {
                    SharedMemory.addAlliedTower(new MapLocation(x, y));
                }
            }
        }
    }
}