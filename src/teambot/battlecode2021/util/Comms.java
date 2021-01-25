package teambot.battlecode2021.util;

import battlecode.common.GameActionException;
import battlecode.common.RobotController;

import java.util.LinkedList;
import java.util.Queue;

public abstract class Comms {

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

    /* Scheduled Communication System*/
    private static int[] schedule = new int[1501];

    public static boolean canScheduleFlag(int turn) {
        if (turn > 1500) {
            return false;
        }
        if (schedule[turn] == 0) {
            return true;
        }
        return false;
    }

    public static boolean scheduleFlag(int turn, int flag) {
        if (controller.canSetFlag(flag)) {
            schedule[turn] = flag;
            return true;
        }
        return false;
    }

    /* Communication Queue System */

    public static void checkAndAddFlag(int flag) throws GameActionException {
        if (controller.canSetFlag(flag)) {
            communicationQueue.add(flag);
        }
    }

    public static int getCommsSize() {
        return communicationQueue.size();
    }

    public static int getFlag(int robotID) throws GameActionException {
        return controller.getFlag(robotID);
    }

    private static Queue<Integer> communicationQueue = new LinkedList<Integer>();
    public static boolean hasSetFlag;

    /* Set all flags here */

    public static void loop() throws GameActionException {
        int flag = schedule[controller.getRoundNum()];
        if (flag != 0) {
            if (controller.canSetFlag(flag)) {
                controller.setFlag(flag);
                hasSetFlag = true;
            }   
        }
        if (!hasSetFlag && communicationQueue.size() > 0) {
            flag = communicationQueue.poll();
            if (controller.canSetFlag(flag)) {
                controller.setFlag(flag);
            }
        }
    }
}
