package finalbot10.battlecode2021.util;

import battlecode.common.*;

public class Util {
    private static RobotController controller;

    private static boolean ECHasScoutedAlready = false;

    public static void init(RobotController controller) {
        Util.controller = controller;
    }

    public static void loop() throws GameActionException {
        Cache.loop();
        Comms.hasSetFlag = false;
    }

    public static void postLoop() throws GameActionException {
        // Scouting
        Cache.CURRENT_LOCATION = controller.getLocation(); //update location to avoid cache bugs in scouting
        Cache.ALL_NEARBY_ENEMY_ROBOTS = controller.senseNearbyRobots(-1, Cache.OPPONENT_TEAM);
        if (!ECHasScoutedAlready && controller.getType() != RobotType.SLANDERER) {
            Scout.scoutMapEdges();
            Scout.scoutSlanderers();
            Scout.scoutECs();
            Scout.scoutEnemies();
        }

        if (Cache.ROBOT_TYPE == RobotType.ENLIGHTENMENT_CENTER) {
            ECHasScoutedAlready = true;
        }

        Comms.loop();
    }

}
