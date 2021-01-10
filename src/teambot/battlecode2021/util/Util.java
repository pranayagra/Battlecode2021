package teambot.battlecode2021.util;

import battlecode.common.GameActionException;
import battlecode.common.RobotController;

public class Util {
    private static RobotController controller;

    public static void init(RobotController controller) {
        Util.controller = controller;
        Cache.init(controller);
    }

    public static void loop() throws GameActionException {
        Cache.loop();
    }

    public static void postLoop() throws GameActionException {

    }

}
