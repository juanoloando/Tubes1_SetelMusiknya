package alternative_bots_2;

import battlecode.common.*;

// Kelas untuk entry point robot. Memilih logika berdasarkan tipe robot dan menjalankan loop utama.
public class RobotPlayer {

    /** Menjalankan logika bot setiap ronde berdasarkan tipe robot. */
    public static void run(RobotController rc) throws GameActionException {
        UnitType type = rc.getType();

        if (type.isTowerType()) {
            new TowerUnit(rc).run();
        } else {
            switch (type) {
                case SOLDIER:  new SoldierUnit(rc).run();  break;
                case SPLASHER: new SplasherUnit(rc).run(); break;
                case MOPPER:   new MopperUnit(rc).run();   break;
                default: break;
            }
        }
    }
}