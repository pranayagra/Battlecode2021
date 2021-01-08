package teambot.battlecode2021.util;

import java.util.ArrayList;

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
    private RobotController controller;

    public Communication (RobotController controller) {
        this.controller = controller;
    }

    public static void send (int order, ArrayList<Integer> data, ArrayList<Integer> bits) {
        int result = 0;
    }

}
