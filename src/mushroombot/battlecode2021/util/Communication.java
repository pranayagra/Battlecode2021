package mushroombot.battlecode2021.util;

import java.util.*;
import battlecode.common.*;
import mushroombot.battlecode2021.util.*;
import mushroombot.battlecode2021.*;
import mushroombot.*;

/*
COMMUNICATION SCHEMA
24 bits

Bit 0-3: Order identifier
Bit 4-9: Location x
Bit 10-15: Location y
Bit 16-22: Data
Bit 23: Parity

ORDERS:
Default
    0 -> Nothing
EC Specific
        New unit order
            1-8 -> Scout
            9 -> Nothing
            10 -> Move


*/

public class Communication {

    private static int SEED = 2193121;
    private static RobotController controller;

    public static void init (RobotController controller) {
        Communication.controller = controller;
    }

    public static boolean trySend (int order, int x, int y, int data) throws GameActionException {
        int result = 0;
        System.out.println("Trying Send");
        result += order << 20;
        result += x << 14;
        result += y << 8;
        result += data << 1;
        if (!parityCheck(result)) {
            result += 1;
        }
        if (controller.canSetFlag(result)) {
            System.out.println("Sending");
            System.out.println(result);
            controller.setFlag(result);
            return true;
        }
        System.out.println(result);
        return false;
    }

    public static int[] recieve(int message) {
        int[] result = new int[4];
        if (parityCheck(message)) {
            result[0] = (message & 15728640) >> 20;
            result[1] = (message & 1032192) >> 14;
            result[2] = (message & 16128) >> 8;
            result[3] = (message & 254) >> 1;
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

}
