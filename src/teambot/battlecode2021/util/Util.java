package teambot.battlecode2021.util;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;

public class Util {
    private static RobotController controller;

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
        Scout.scoutMapEdges();
        Scout.scoutECs();
        Scout.scoutEnemies();
        Comms.loop();
    }

}
