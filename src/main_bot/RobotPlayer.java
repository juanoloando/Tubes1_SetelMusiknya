package main_bot;

import battlecode.common.*;

public class RobotPlayer {

    public static void run(RobotController rc) throws GameActionException {
        UnitType type = rc.getType();

        if (type.isTowerType()) {
            new TowerUnit(rc).run();
        } else {
            switch (type) {
                case SOLDIER:  new SoldierUnit(rc).run(); break;
                case SPLASHER: new SplasherUnit(rc).run(); break;
                case MOPPER:   new MopperUnit(rc).run(); break;
                default: break;
            }
        }
    }
}
