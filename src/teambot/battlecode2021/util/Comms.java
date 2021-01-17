package teambot.battlecode2021.util;

import battlecode.common.GameActionException;
import battlecode.common.RobotController;

import java.util.LinkedList;
import java.util.Queue;

public abstract class Comms {

    private static int SEED = 2193121;
    public static RobotController controller;
    public static void init(RobotController controller) {
        Comms.controller = controller;
    }

    public static boolean decodeIsUrgent(int encoding) {
        return ((encoding >> 20) & 1) == 1;
    }

    public static boolean decodeIsLastFlag(int encoding) {
        return ((encoding >> 19) & 1) == 1;
    }

    /* Communication Queue System */

    public static void checkAndAddFlag(int flag) throws GameActionException {
        if (controller.canSetFlag(flag)) {
            communicationQueue.add(flag /*^ SEED*/);
        }
    }

    public static int getFlag(int robotID) throws GameActionException {
        return controller.getFlag(robotID) /*^ SEED*/;
    }

    private static Queue<Integer> communicationQueue = new LinkedList<Integer>();
    public static boolean hasSetFlag;

    public static void loop() throws GameActionException {
        if (!hasSetFlag && communicationQueue.size() > 0) {
            int flag = communicationQueue.poll();
            if (controller.canSetFlag(flag)) {
                controller.setFlag(flag);
            }
        }
    }
}
