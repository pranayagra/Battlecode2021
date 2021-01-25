package teambot3oldframeworkbutnewattack.battlecode2021.util;

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

        if (!ECHasScoutedAlready) {
            Scout.scoutMapEdges();
            Scout.scoutECs();
            Scout.scoutEnemies();
        }

        if (Cache.ROBOT_TYPE == RobotType.ENLIGHTENMENT_CENTER) {
            ECHasScoutedAlready = true;
        }

        Comms.loop();
    }

}
