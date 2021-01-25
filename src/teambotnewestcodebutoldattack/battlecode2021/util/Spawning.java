package teambotnewestcodebutoldattack.battlecode2021.util;

import battlecode.common.*;

public class Spawning {

    public static RobotController controller;

    public static void init(RobotController controller) {
        Scout.controller = controller;
    }

    // Slanderers

    public static int[] SLANDER_SPAWN_TABLE = new int[] {
        21, 41, 63, 85, 107, 130, 154, 178, 203, 228,
        255, 282, 310, 339, 368, 399, 431, 463, 497,
        532, 568, 605, 643, 683, 724, 766, 810, 855, 902,
        949
    };

    public static int getSpawnInfluence(double influence) {

        int res = 0;

        for (int i : SLANDER_SPAWN_TABLE) {
            if (i <= influence) {
                res = i;
            }
        }

        return res;

    }

}
