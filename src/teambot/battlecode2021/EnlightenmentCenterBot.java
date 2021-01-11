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
    public static int[] ALL_MY_EC_IDS;
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
        ALL_MY_EC_IDS = new int[MAX_ECS_PER_TEAM * 4];
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

        if (EC_ID_CURRENT_BRUTE_FORCE <= EC_ID_END_BRUTE_FORCE + 2) {
            brute_force_ids();
            Debug.printECInformation();
        }

        if (Clock.getBytecodesLeft() <= 2000) {
            return;
        }

        readECFlags();



    }

    public void readECFlags() throws GameActionException {
        int[] msgs = new int[num_ALL_MY_EC_LOCATIONs];
        for (int i = 0; i < num_ALL_MY_EC_LOCATIONs; ++i) {
            if (controller.canGetFlag(ALL_MY_EC_IDS[i])) {
                msgs[i] = controller.getFlag(ALL_MY_EC_IDS[i]);
                if (msgs[i] >> 20 == 0b1111) {
                    continue;
                } else {
                    // This is where to attack message... -> out of all the flags
                    // Protocol -> robots scout and try to find enemy ECs. If it finds a location, we set the flag and the EC can then read it.
                    // It can save the enemy location and either 1) set the protocol on where to send all targets (if no other flag is set for other ECs) or output the same flag as another EC.
                    //By the end of the round (or beginning of next round), each EC should have the same attack flag.

                    //If a robot shows won (then EC will win status) => and other ECs can read it.
                    //We either scout another location

                }
            }
        }


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
                    MapLocation ECLOC = Communication.decodeLocation(flag);
                    if (ECLOC.x == -1 || ECLOC.y == -1) continue;
                    ALL_MY_EC_LOCATIONS[num_ALL_MY_EC_LOCATIONs] = ECLOC;
                    ALL_MY_EC_IDS[num_ALL_MY_EC_LOCATIONs++] = validIDS[i];
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
