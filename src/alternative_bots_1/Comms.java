package alternative_bots_1;

import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.Message;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;

public class Comms {
    static final int MSG_SPAWN = 1;
    static final int MSG_DANGER = 2;
    static final int MSG_CLAIM = 3;

    static MapLocation cachedSpawn = null;
    static MapLocation cachedDanger = null;
    static int dangerRound = -1;
    static MapLocation[] claimedRuins = new MapLocation[40];
    static int claimIdx = 0;

    static int encode(int type, MapLocation loc) {
        int val = (loc == null) ? 0 : ((loc.x + 1) << 6) | (loc.y + 1);
        return (type << 20) | val;
    }

    static void processMessages(RobotController rc) {
        Message[] msgs = rc.readMessages(-1);
        for (Message m : msgs) {
            int data = m.getBytes();
            int type = (data >>> 20) & 0xF;
            int val = data & 0xFFFFF;
            MapLocation loc = (val == 0) ? null : new MapLocation((val >> 6) - 1, (val & 0x3F) - 1);

            if (type == MSG_SPAWN)
                cachedSpawn = loc;
            else if (type == MSG_DANGER) {
                cachedDanger = loc;
                dangerRound = rc.getRoundNum();
            } else if (type == MSG_CLAIM && loc != null) {
                boolean found = false;
                for (MapLocation r : claimedRuins) {
                    if (r != null && r.equals(loc)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    claimedRuins[claimIdx] = loc;
                    claimIdx = (claimIdx + 1) % claimedRuins.length;
                }
            }
        }
        if (dangerRound != -1 && rc.getRoundNum() - dangerRound > 15) {
            cachedDanger = null;
        }
    }

    static void broadcastEnemySpawn(RobotController rc, MapLocation loc) throws GameActionException {
        cachedSpawn = loc;
        int msg = encode(MSG_SPAWN, loc);
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo ally : allies) {
            if (rc.canSendMessage(ally.getLocation(), msg))
                rc.sendMessage(ally.getLocation(), msg);
        }
    }

    static MapLocation getEnemySpawn(RobotController rc) throws GameActionException {
        return cachedSpawn;
    }

    static void pingDanger(RobotController rc, MapLocation loc) throws GameActionException {
        cachedDanger = loc;
        dangerRound = rc.getRoundNum();
        int msg = encode(MSG_DANGER, loc);
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo ally : allies) {
            if (ally.getType().isTowerType() && rc.canSendMessage(ally.getLocation(), msg)) {
                rc.sendMessage(ally.getLocation(), msg);
                return;
            }
        }
        for (RobotInfo ally : allies) {
            if (rc.canSendMessage(ally.getLocation(), msg))
                rc.sendMessage(ally.getLocation(), msg);
        }
    }

    static MapLocation readDanger(RobotController rc) throws GameActionException {
        return cachedDanger;
    }

    static void claimRuin(RobotController rc, MapLocation loc) throws GameActionException {
        boolean found = false;
        for (MapLocation r : claimedRuins) {
            if (r != null && r.equals(loc)) {
                found = true;
                break;
            }
        }
        if (!found) {
            claimedRuins[claimIdx] = loc;
            claimIdx = (claimIdx + 1) % claimedRuins.length;
        }
        int msg = encode(MSG_CLAIM, loc);
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo ally : allies) {
            if (rc.canSendMessage(ally.getLocation(), msg))
                rc.sendMessage(ally.getLocation(), msg);
        }
    }

    static boolean isRuinClaimed(RobotController rc, MapLocation loc) throws GameActionException {
        for (MapLocation r : claimedRuins) {
            if (r != null && r.equals(loc))
                return true;
        }
        return false;
    }
}
