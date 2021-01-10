package teambot.battlecode2021.util;

import java.util.ArrayList;

import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.Team;

/*
COMMUNICATION SCHEMA
24 bits

[0000]
Bit 0-3: Order identifier

[00000000000000000000]
Bit 
*/

public class Communication {

    private static int SEED = 2193121;
    public static RobotController controller;

    public Communication (RobotController controller) {
        this.controller = controller;
    }

    public static void send (int order) {

    }

    public static int makeFlag_MYLOCATION(String locationMeaning) {
        int code = 0b0000;

        switch (locationMeaning) {
            case "LOCATION":
                code = 0b0000;
                break;
            case "MAP_RIGHT_SIDE":
                code = 0b0001;
                break;
            case "MAP_TOP_SIDE":
                code = 0b0010;
                break;
            case "MAP_LEFT_SIDE":
                code = 0b0100;
                break;
            case "MAP_BOTTOM_SIDE":
                code = 0b1000;
                break;
            case "MAP_WIDTH":
                code = 0b0101;
                break;
            case "MAP_HEIGHT":
                code = 0b1010;
                break;
        }

        int flag = (code << 20) + ((Cache.CURRENT_LOCATION.x % 1000) << 10) + (Cache.CURRENT_LOCATION.y % 1000);
        return flag;
    }

//    public static int makeFlag_ATTACK_EC(MapLocation location) {
//
//    }

    public static Object decode(int flag) {

//        switch (flag >> 20) {
//            case 0b0001:
//                flagType = "Location";
//                break;
//            case 0b1111:
//                flagType = "ECID";
//                break;
//        }

        return flag;
    }

    public static int makeFlag_MYEC_ID(int id) {
        int flag = (0b1111 << 20) + id;
        return flag;
    }

}
