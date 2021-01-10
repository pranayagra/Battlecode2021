package mushroombot.battlecode2021.util;

import java.util.*;
import battlecode.common.*;
import mushroombot.battlecode2021.util.*;
import mushroombot.battlecode2021.*;
import mushroombot.*;

/*
COMMUNICATION SCHEMA
24 bits

Bit 0-5: Order identifier
Bit 6-11: Location x
Bit 12-17: Location y
Bit 18-22: Data
Bit 23: Parity

ORDERS:
Default
    0 -> Nothing
EC Specific
        New unit order
            1-8 -> Scout
            9 -> Nothing
            10 -> Move

Scouts
    20 -> Edge of Map


*/

public class Communication {

    // Communication
    private static Queue<Integer> MESSAGE_QUEUE = new LinkedList<Integer>();
    public static boolean HAS_SET_FLAG = false;
    private static int SEED = 2193121;
    private static RobotController controller;

    public static void init (RobotController controller) {
        Communication.controller = controller;
    }

    // Recieves a message information and adds to queue
    public static boolean trySend (int order, int x, int y, int data) {
        int result = 0;
        result += order << 18;
        result += x << 12;
        result += y << 6;
        result += data << 1;
        if (!parityCheck(result)) {
            result += 1;
        }
        if (controller.canSetFlag(result)) {
            MESSAGE_QUEUE.add(result);
            return true;
        }
        System.out.println(result);
        return false;
    }

    public static boolean prioritySend (int order, int x, int y, int data) throws GameActionException {
        int result = 0;
        result += order << 18;
        result += x << 12;
        result += y << 6;
        result += data << 1;
        if (!parityCheck(result)) {
            result += 1;
        }
        if (controller.canSetFlag(result)) {
            controller.setFlag(result);
            HAS_SET_FLAG = true;
            return true;
        }
        System.out.println(result);
        return false;
    }

    public static boolean trySend(int message) {
        System.out.println(message);
        if (parityCheck(message)) {
            if (controller.canSetFlag(message)) {
                MESSAGE_QUEUE.add(message);
                return true;
            }
        }
        return false;
    }

    public static int[] recieve(int message) {
        int[] result = new int[4];
        if (parityCheck(message)) {
            result[0] = (message & 0b111111000000000000000000) >> 18;
            result[1] = (message & 0b000000111111000000000000) >> 12;
            result[2] = (message & 0b000000000000111111000000) >> 6;
            result[3] = (message & 0b000000000000000000111110) >> 1;
            return result;
        }
        return null;
    }

    public static boolean parityCheck(int n) {
        int count = 0;
        while (n!=0)
        {
            n = n & (n-1);
            count++;
        }
        if (count % 2 == 1) {
            return false;
        }
        return true;
    }

    public static void post() throws GameActionException {
        if (!HAS_SET_FLAG && MESSAGE_QUEUE.size() > 0) {
            controller.setFlag(MESSAGE_QUEUE.poll());
        }
    }

}
