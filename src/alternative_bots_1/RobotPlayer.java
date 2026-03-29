package alternative_bots_1;

import battlecode.common.*;
import java.util.Random;

public class RobotPlayer {
    static final Random rng = new Random();

    public static void run(RobotController rc) throws GameActionException {
        while (true) {
            try {
                Comms.processMessages(rc);
                if (rc.getType().isTowerType()) {
                    Tower.run(rc);
                } else {
                    switch (rc.getType()) {
                        case SOLDIER:
                            Soldier.run(rc);
                            break;
                        case SPLASHER:
                            Splasher.run(rc);
                            break;
                        case MOPPER:
                            Mopper.run(rc);
                            break;
                        default:
                            break;
                    }
                }
            } catch (Exception e) {
                System.out.println("Exception in Bot Counter:");
                e.printStackTrace();
            }
            Clock.yield();
        }
    }
}