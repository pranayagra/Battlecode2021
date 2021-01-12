package teambot.battlecode2021;

import battlecode.common.*;
import teambot.*;
import teambot.battlecode2021.util.Cache;
import teambot.battlecode2021.util.Communication;
import teambot.battlecode2021.util.Constants;
import teambot.battlecode2021.util.Debug;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;

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

    public static int num_ALL_ENEMY_EC_LOCATIONs;
    public static MapLocation[] ALL_ENEMY_EC_LOCATIONS;

    private final int MAX_ECS_PER_TEAM = 3;

    private static Random random;

    public EnlightenmentCenterBot(RobotController controller) throws GameActionException {
        this.controller = controller;
        random = new Random(controller.getID());
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

        num_ALL_ENEMY_EC_LOCATIONs = 0;
        ALL_ENEMY_EC_LOCATIONS = new MapLocation[MAX_ECS_PER_TEAM * 4];
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
        readMyRobotFlags();

    }

    public void readMyRobotFlags() throws GameActionException {

        for (Iterator<Integer> i = Cache.EC_ALL_PRODUCED_ROBOT_IDS.iterator(); i.hasNext();) {
            Integer robotID = i.next();
            if (controller.canGetFlag(robotID)) {
                int robotMSG = controller.getFlag(robotID);
                MapLocation enemyLocation = Communication.decodeLocationData(robotMSG);
                ALL_ENEMY_EC_LOCATIONS[num_ALL_ENEMY_EC_LOCATIONs++] = enemyLocation;
            } else {
                i.remove();
            }

            if (Clock.getBytecodesLeft() <= 300) {
                break;
            }
        }
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

        boolean spawnMucraker = random.nextInt(2) == 1;

        if (spawnMucraker) {
            tryBuildMuckraker(1);
        } else {

        }



    }

    public void setLocationFlag() throws GameActionException {
        int encodedFlag = Communication.encode_LocationType_and_LocationData(Constants.FLAG_LOCATION_TYPES.MY_EC_LOCATION, Cache.CURRENT_LOCATION);
        Debug.printInformation("setLocationFlag()", encodedFlag);
        Communication.checkAndSetFlag(encodedFlag);
    }

    public void brute_force_ids() throws GameActionException {
        //brute force IDs
        for (int i = 0; i < num_validIDS; ++i) {

            int encodedFlag = Communication.checkAndGetFlag(validIDS[i]);
            if (encodedFlag == -1) continue;

            if (Communication.decodeIsFlagLocationType(encodedFlag, true)) {

                Constants.FLAG_LOCATION_TYPES flagType = Communication.decodeLocationType(encodedFlag);
                if (flagType != Constants.FLAG_LOCATION_TYPES.MY_EC_LOCATION) continue;

                MapLocation location = Communication.decodeLocationData(encodedFlag);
                ALL_MY_EC_LOCATIONS[num_ALL_MY_EC_LOCATIONs] = location;
                ALL_MY_EC_IDS[num_ALL_MY_EC_LOCATIONs++] = validIDS[i];
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

    public boolean tryBuildPolitician(int influence) throws  GameActionException {

        if (!controller.isReady()) {
            return false;
        }

        for (Direction dir : RobotPlayer.directions) {
            if (controller.canBuildRobot(RobotType.POLITICIAN, dir, influence)) {
                controller.buildRobot(RobotType.POLITICIAN, dir, influence);
                Cache.EC_ALL_PRODUCED_ROBOT_IDS.add(controller.senseRobotAtLocation(Cache.CURRENT_LOCATION.add(dir)).ID);
                ++POLITICIAN_NUM;
                return true;
            }
        }
        return false;
    }
}
