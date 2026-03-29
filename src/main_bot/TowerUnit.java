package main_bot;

import battlecode.common.*;

public class TowerUnit extends BaseRobot {

    private int soldierSpawned = 0;
    private int splasherSpawned = 0;
    private int mopperSpawned = 0;

    public TowerUnit(RobotController rc) {
        super(rc);
    }

    @Override
    protected void tick() throws GameActionException {
        readAndBroadcastMessages();
        tryUpgradeTower();
        trySpawnUnit();
        tryAttackEnemy();
    }

    private void readAndBroadcastMessages() throws GameActionException {
        for (Message msg : rc.readMessages(-1)) {
            int content = msg.getBytes();
            if ((content & 0x80000000) != 0) {
                int x = (content >> 16) & 0x7FFF;
                int y = content & 0xFFFF;
                addEnemyTower(new MapLocation(x, y));
            }
        }
        if (rc.canBroadcastMessage()) {
            MapLocation myLoc = rc.getLocation();
            int encoded = (myLoc.x << 16) | myLoc.y;
            rc.broadcastMessage(encoded);
        }
    }

    private void tryUpgradeTower() throws GameActionException {
        int round = rc.getRoundNum();
        int chips = rc.getMoney();
        MapLocation myLoc = rc.getLocation();
        UnitType myType = rc.getType();

        if (myType == UnitType.LEVEL_ONE_PAINT_TOWER && round > 300 && chips > 3000) {
            if (rc.canUpgradeTower(myLoc)) rc.upgradeTower(myLoc);
        } else if (myType == UnitType.LEVEL_TWO_PAINT_TOWER && round > 700 && chips > 5000) {
            if (rc.canUpgradeTower(myLoc)) rc.upgradeTower(myLoc);
        }
    }

    private void trySpawnUnit() throws GameActionException {
        if (!rc.isActionReady()) return;

        int chips = rc.getMoney();
        int paint = rc.getPaint();
        if (paint < 200) return;

        // Dua soldier awal dipisah agar cakupan arah pembukaan lebih luas.
        if (rc.getRoundNum() < 30 && soldierSpawned < 2) {
            // Spawn pertama dan kedua memakai blok arah yang saling berlawanan.
            // Kombinasi indeks 0..3 dan 4..7 menjaga distribusi awal tetap seimbang.
            int startIdx = (soldierSpawned == 0) ? 0 : 4;
            
            for (int i = 0; i < DIRS.length; i++) {
                Direction dir = DIRS[(startIdx + i) % DIRS.length];
                MapLocation spawnLoc = rc.getLocation().add(dir);
                if (rc.canBuildRobot(UnitType.SOLDIER, spawnLoc)) {
                    rc.buildRobot(UnitType.SOLDIER, spawnLoc);
                    soldierSpawned++;
                    return;
                }
            }
            return; 
        }

        int round = rc.getRoundNum();
        int total = soldierSpawned + splasherSpawned + mopperSpawned;

        UnitType toSpawn = decideSpawnType(round, total, chips);
        if (toSpawn == null) return;

        for (Direction dir : DIRS) {
            MapLocation spawnLoc = rc.getLocation().add(dir);
            if (rc.canBuildRobot(toSpawn, spawnLoc)) {
                rc.buildRobot(toSpawn, spawnLoc);
                countSpawn(toSpawn);
                return;
            }
        }
    }

    private UnitType decideSpawnType(int round, int total, int chips) {
        boolean hasEnemyThreat = enemyTowerCount > 0;
        if (round < 150) {
            if (soldierSpawned <= splasherSpawned) return UnitType.SOLDIER;
            return UnitType.SPLASHER;
        }
        if (hasEnemyThreat && mopperSpawned < total / 4) return UnitType.MOPPER;

        int soldierRatio = total == 0 ? 0 : soldierSpawned * 100 / total;
        int splasherRatio = total == 0 ? 0 : splasherSpawned * 100 / total;
        int mopperRatio = total == 0 ? 0 : mopperSpawned * 100 / total;

        if (mopperRatio < 15) return UnitType.MOPPER;
        if (soldierRatio < 40) return UnitType.SOLDIER;
        if (splasherRatio < 45) return UnitType.SPLASHER;

        int roll = rng.nextInt(3);
        if (roll == 0) return UnitType.SOLDIER;
        if (roll == 1) return UnitType.SPLASHER;
        return UnitType.MOPPER;
    }

    private void countSpawn(UnitType type) {
        switch (type) {
            case SOLDIER:  soldierSpawned++;  break;
            case SPLASHER: splasherSpawned++; break;
            case MOPPER:   mopperSpawned++;   break;
            default: break;
        }
    }

    private void tryAttackEnemy() throws GameActionException {
        if (!rc.isActionReady()) return;
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (enemies.length > 0) {
            MapLocation target = enemies[0].getLocation();
            if (rc.canAttack(target)) rc.attack(target);
        }
    }
}