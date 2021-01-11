package teambot.battlecode2021;

import battlecode.common.*;
import teambot.*;
import teambot.battlecode2021.util.Cache;
import teambot.battlecode2021.util.Communication;
import teambot.battlecode2021.util.Debug;

import java.util.ArrayList;
import java.util.Map;

public class EnlightenmentCenterBot implements RunnableBot {
    private RobotController controller;

    private static int EC_ID_CURRENT_BRUTE_FORCE;
    private static int EC_ID_END_BRUTE_FORCE;

    private int MUCKRAKER_NUM = 0;
    private int SLANDERER_NUM = 0;
    private int POLITICIAN_NUM = 0;
    private int SCOUT_DIRECTION = 0;

    public static int num_validIDS;
    public static int[] validIDS;

    public static int num_ALL_MY_EC_LOCATIONs;
    public static MapLocation[] ALL_MY_EC_LOCATIONS;

    private final int MAX_ECS_PER_TEAM = 3;

    public EnlightenmentCenterBot(RobotController controller) throws GameActionException {
        this.controller = controller;
        init();
    }

    @Override
    public void init() throws GameActionException {
        EC_ID_CURRENT_BRUTE_FORCE = 10000;
        EC_ID_END_BRUTE_FORCE = EC_ID_CURRENT_BRUTE_FORCE + 4096;
        num_validIDS = 0;
        validIDS = new int[MAX_ECS_PER_TEAM * 4];

        num_ALL_MY_EC_LOCATIONs = 0;
        ALL_MY_EC_LOCATIONS = new MapLocation[MAX_ECS_PER_TEAM * 4];
    }

    @Override
    public void turn() throws GameActionException {
        Debug.printRobotInformation();
        Debug.printMapInformation();

        switch (controller.getRoundNum()) {
            case 1:
                setLocationFlag();
                tryBuildSlanderer(50);
                break;
            default:
                defaultTurn();
                break;
        }

        if (EC_ID_CURRENT_BRUTE_FORCE <= EC_ID_END_BRUTE_FORCE) {
            brute_force_ids();
        }

        Debug.printECInformation();

    }

    public void defaultTurn() throws GameActionException {

        if (MUCKRAKER_NUM < 8) {
            tryBuildMuckraker(1);
            return;
        }



    }

    public void setLocationFlag() throws GameActionException {
        int flag = Communication.makeFlag_MYLOCATION("LOCATION");
        if (controller.canSetFlag(flag)) {
            controller.setFlag(flag);
        }
    }

    public void brute_force_ids() throws GameActionException {
        //brute force IDs
        for (int i = 0; i < num_validIDS; ++i) {
            if (controller.canGetFlag(validIDS[i])) {
                int flag = controller.getFlag(validIDS[i]);
                if (flag >> 20 == 0b1111) {
                    ALL_MY_EC_LOCATIONS[num_ALL_MY_EC_LOCATIONs++] = Communication.decodeLocation(flag);
                }
            }
        }
        num_validIDS = 0;
        while (EC_ID_CURRENT_BRUTE_FORCE++ <= EC_ID_END_BRUTE_FORCE) {
            if (controller.canGetFlag(EC_ID_CURRENT_BRUTE_FORCE)) {
                validIDS[num_validIDS++] = EC_ID_CURRENT_BRUTE_FORCE;
            }

            if (Clock.getBytecodesLeft() <= 400) {
                break;
            }
        }

        Debug.printByteCode("CHECKED MORE IDS -- " + (EC_ID_CURRENT_BRUTE_FORCE));
    }

    // THE FACTORIES
    public boolean tryBuildSlanderer(int influence) throws GameActionException {

        if (!controller.isReady()) {
            return false;
        }

        for (Direction dir : RobotPlayer.directions) {
            if (controller.canBuildRobot(RobotType.SLANDERER, dir, influence)) {
                controller.buildRobot(RobotType.SLANDERER, dir, influence);
                Cache.EC_ALL_PRODUCED_ROBOT_IDS.add(controller.senseRobotAtLocation(Cache.CURRENT_LOCATION.add(dir)).ID);
                ++SLANDERER_NUM;
                return true;
            }
        }
        return false;
    }

    public boolean tryBuildMuckraker(int influence) throws  GameActionException {

        if (!controller.isReady()) {
            return false;
        }

        for (Direction dir : RobotPlayer.directions) {
            if (controller.canBuildRobot(RobotType.MUCKRAKER, dir, influence)) {
                controller.buildRobot(RobotType.MUCKRAKER, dir, influence);
                Cache.EC_ALL_PRODUCED_ROBOT_IDS.add(controller.senseRobotAtLocation(Cache.CURRENT_LOCATION.add(dir)).ID);
                ++MUCKRAKER_NUM;
                return true;
            }
        }
        return false;
    }
}
