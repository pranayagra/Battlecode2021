package teambot.battlecode2021;

import battlecode.common.*;
import teambot.*;
import teambot.battlecode2021.util.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;

public class EnlightenmentCenterBot implements RunnableBot {
    private RobotController controller;

    private static int EC_ID_CURRENT_BRUTE_FORCE;
    private static int EC_ID_END_BRUTE_FORCE;

    /* Politicians that are attacking -- mid prio (should only be at most 100) */
    private static int ATTACKING_POLITICIAN_SZ;
    private static int[] ATTACKING_POLITICIAN_IDs;

    /* Politicians that are defending slanderers -- high prio */
    private static int POLITICIAN_DEFENDING_SLANDERER_SZ;
    private static int[] POLITICIAN_DEFENDING_SLANDERER_IDs;

    /* Slanderer, useful to communicate danger or production of muckrakers -- mid prio (depending on implemention of wall) */
    private static FastQueueSlanderers SLANDERER_IDs;

    /* I do not think we need to store these, maybe only if we want to communicate to the EC we are getting attacked heavily -- low prio */
    private static int WALL_MUCKRAKER_SZ;
    private static int[] WALL_MUCKRAKER_IDs;

    /* Should always iterate on all of these -- highest priority (should be at most 100) */
    private static int SCOUT_MUCKRAKER_SZ;
    private static int[] SCOUT_MUCKRAKER_IDs;

    //TODO: need to think of building requests when we cannot build on a certain round or have too many requests.. we probably want some type of priority here
    private static int[] buildRequests; //want to store -> the necessary information (type, influence, direction, deltaDirectionAllowed) and how old the request is?

    private static int num_validIDS;
    private static int[] validIDS;


    public static int num_ALL_MY_EC_LOCATIONs;
    public static int[] ALL_MY_EC_IDS;
    public static MapLocation[] ALL_MY_EC_LOCATIONS;

    public static int num_ALL_ENEMY_EC_LOCATIONs;
    public static MapLocation[] ALL_ENEMY_EC_LOCATIONS;

    private final int MAX_ECS_PER_TEAM = 3;

    private static Random random;

    public EnlightenmentCenterBot(RobotController controller) throws GameActionException {
        this.controller = controller;
        init();
    }

    @Override
    public void init() throws GameActionException {
        random = new Random(controller.getID());

        EC_ID_CURRENT_BRUTE_FORCE = 10000;
        EC_ID_END_BRUTE_FORCE = EC_ID_CURRENT_BRUTE_FORCE + 4096;
        num_validIDS = 0;
        validIDS = new int[MAX_ECS_PER_TEAM * 4];

        num_ALL_MY_EC_LOCATIONs = 0;
        ALL_MY_EC_LOCATIONS = new MapLocation[MAX_ECS_PER_TEAM * 4];
        ALL_MY_EC_IDS = new int[MAX_ECS_PER_TEAM * 4];

        num_ALL_ENEMY_EC_LOCATIONs = 0;
        ALL_ENEMY_EC_LOCATIONS = new MapLocation[MAX_ECS_PER_TEAM * 4];

        Debug.printByteCode("BEFORE ARRAY INIT ");
//        MUCKRAKER_IDs = new int[64 * 64];
//        POLITICIAN_IDs = new int[64 * 64];
//        SLANDERER_IDs = new int[64 * 64];

        SCOUT_MUCKRAKER_IDs = new int[100];
        SCOUT_MUCKRAKER_SZ = 0;

        SLANDERER_IDs = new FastQueueSlanderers(152);
        POLITICIAN_DEFENDING_SLANDERER_IDs = new int[200];

        Debug.printByteCode("AFTER ARRAY INIT ");
        Debug.printByteCode("AFTER ITERATION 64^2 TIMES ");

    }

    @Override
    public void turn() throws GameActionException {
        Debug.printRobotInformation();
        Debug.printMapInformation();

        switch (controller.getRoundNum()) {
            case 1:
                round1();
                return;
            case 2:
                defaultTurn();
                round2();
                break;
            default:
                defaultTurn();
                break;
        }

        if (EC_ID_CURRENT_BRUTE_FORCE <= EC_ID_END_BRUTE_FORCE) {
            brute_force_ids();
        }
        Debug.printECInformation();

        if (Clock.getBytecodesLeft() <= 2000) {
            return;
        }
    }

    public void updateSlanderers() {
        if (!SLANDERER_IDs.isEmpty()) {
            if (controller.getRoundNum() - SLANDERER_IDs.getFrontCreationTime() >= 300) { //need to retire slanderer as 1) it got killed or 2) converted
                int id = SLANDERER_IDs.getFrontID();
                SLANDERER_IDs.removeFront();
                if (controller.canGetFlag(id) && POLITICIAN_DEFENDING_SLANDERER_SZ < POLITICIAN_DEFENDING_SLANDERER_IDs.length) {
                    POLITICIAN_DEFENDING_SLANDERER_IDs[POLITICIAN_DEFENDING_SLANDERER_SZ++] = id;
                }
            }
        }
    }

    public void readFriendlyECFlags() {

    }

    public void readFriendlyScoutFlags() {

    }


    /*
    *
    *
    * */

    public void defaultTurn() throws GameActionException {
        updateSlanderers();
        readFriendlyScoutFlags();
        readFriendlyECFlags();

//        if (MUCKRAKER_NUM < 8) {
//            tryBuildMuckraker(1);
//            return;
//        }

//        tryBuildSlanderer(1);
//
////        boolean spawnMuckraker = random.nextInt(2) == 1;
////        if (spawnMuckraker) {
////            tryBuildMuckraker(1);
////        } else {
////            tryBuildSlanderer(2);
////        }


        // int spawnType = random.nextInt(2);
        // if (spawnType == 0) {
        //     tryBuildPolitician(13);
        // } else if (spawnType == 1) {
        //     tryBuildSlanderer(1);
        // }



        if (controller.getRoundNum() > 10) {
            if (Cache.ALL_NEARBY_FRIENDLY_ROBOTS.length < 100) {
                spawnWallMuckraker(2, randomValidDirection());
            }
        } else {
            spawnScoutMuckraker(1, randomValidDirection());
        }
    }

    private void setLocationFlag() throws GameActionException {
        int encodedFlag = Communication.encode_ExtraANDLocationType_and_ExtraANDLocationData(
                Constants.FLAG_EXTRA_TYPES.VERIFICATION_ENSURANCE, Constants.FLAG_LOCATION_TYPES.MY_EC_LOCATION, 0, Cache.CURRENT_LOCATION);
        Debug.printInformation("setLocationFlag()", encodedFlag);
        Communication.checkAndSetFlag(encodedFlag);
    }

    private void round1() throws GameActionException {
        setLocationFlag();
        spawnLatticeSlanderer(50, randomValidDirection());
        while (Clock.getBytecodesLeft() >= 300 && EC_ID_CURRENT_BRUTE_FORCE++ <= EC_ID_END_BRUTE_FORCE) {
            if (controller.canGetFlag(EC_ID_CURRENT_BRUTE_FORCE)) {
                validIDS[num_validIDS++] = EC_ID_CURRENT_BRUTE_FORCE;
            }

        }
    }

    private void round2() throws GameActionException {
        for (int i = 0; i < num_validIDS; ++i) {

            int encodedFlag = Communication.checkAndGetFlag(validIDS[i]);
            if (encodedFlag == -1) continue;

            if (Communication.decodeIsFlagLocationType(encodedFlag, true)) {

                Constants.FLAG_LOCATION_TYPES locationType = Communication.decodeLocationType(encodedFlag);
                if (locationType != Constants.FLAG_LOCATION_TYPES.MY_EC_LOCATION) continue;

                MapLocation location = Communication.decodeLocationData(encodedFlag);
                ALL_MY_EC_LOCATIONS[num_ALL_MY_EC_LOCATIONs] = location;
                ALL_MY_EC_IDS[num_ALL_MY_EC_LOCATIONs++] = validIDS[i];
            }
        }
    }

    private void brute_force_ids() throws GameActionException {
        //brute force IDs
        while (Clock.getBytecodesLeft() >= 400 && EC_ID_CURRENT_BRUTE_FORCE++ <= EC_ID_END_BRUTE_FORCE) {
            int encodedFlag = Communication.checkAndGetFlag(EC_ID_CURRENT_BRUTE_FORCE);
            if (encodedFlag == -1) continue;
            if (Communication.decodeIsFlagLocationType(encodedFlag, true)) {
                Constants.FLAG_LOCATION_TYPES locationType = Communication.decodeLocationType(encodedFlag);
                if (locationType != Constants.FLAG_LOCATION_TYPES.MY_EC_LOCATION) continue;

                MapLocation locationData = Communication.decodeLocationData(encodedFlag);
                ALL_MY_EC_LOCATIONS[num_ALL_MY_EC_LOCATIONs] = locationData;
                ALL_MY_EC_IDS[num_ALL_MY_EC_LOCATIONs++] = EC_ID_CURRENT_BRUTE_FORCE;
            }
        }
    }

    /* Greedily returns the closest valid direction to preferredDirection within the directionFlexibilityDelta value (2 means allow for 2 clockwise 45 deg in both directions)
    Returns null if no valid direction with specification
    directionFlexibilityDelta: max value 4 */
    private Direction toBuildDirection(Direction preferredDirection, int directionFlexibilityDelta) {

        if (!controller.isReady()) return null;

        if (controller.canBuildRobot(RobotType.MUCKRAKER, preferredDirection, 1)) {
            return preferredDirection;
        }

        Direction left = preferredDirection;
        Direction right = preferredDirection;
        for (int i = 1; i <= directionFlexibilityDelta; ++i) {
            right = right.rotateRight();
            left = left.rotateLeft();
            if (controller.canBuildRobot(RobotType.MUCKRAKER, right, 1)) return right;
            if (controller.canBuildRobot(RobotType.MUCKRAKER, left, 1)) return left;
        }
        return null;
    }

    /* Finds a random valid direction.
    returns null if no valid direction */
    private Direction randomValidDirection() {
        return toBuildDirection(Constants.DIRECTIONS[random.nextInt(8)], 4);
    }

    private void spawnScoutMuckraker(int influence, Direction direction) throws GameActionException {
        //TODO: should spawn muckraker in location
        if (controller.canBuildRobot(RobotType.MUCKRAKER, direction, influence)) {
            controller.buildRobot(RobotType.MUCKRAKER, direction, influence);

        }

    }

    private void spawnWallMuckraker(int influence, Direction direction) throws GameActionException {
        //TODO: should spawn muckrakers to build wall
        if (controller.canBuildRobot(RobotType.MUCKRAKER, direction, influence)) {
            controller.buildRobot(RobotType.MUCKRAKER, direction, influence);
        }
    }

    private void spawnLatticeSlanderer(int influence, Direction direction) throws GameActionException {
        //TODO: should spawn slanderer, which default behavior is to build lattice
        if (controller.canBuildRobot(RobotType.SLANDERER, direction, influence)) {
            controller.buildRobot(RobotType.SLANDERER, direction, influence);
            SLANDERER_IDs.push(controller.senseRobotAtLocation(Cache.CURRENT_LOCATION.add(direction)).ID, controller.getRoundNum());
        }
    }

    private void spawnDefendingPolitician(int influence, Direction direction) throws GameActionException {
        //TODO: should defend slanderers outside the muckrakers wall
        if (controller.canBuildRobot(RobotType.POLITICIAN, direction, influence)) {
            controller.buildRobot(RobotType.POLITICIAN, direction, influence);
        }
    }

    private void spawnAttackingPolitician(int influence, Direction direction) throws GameActionException {
        //TODO: should only be called if the politician is meant to attack some base -> need to create politician with enough influence, set my EC flag to the location + attacking poli
        //Assumption: politician upon creation should read EC flag and know it's purpose in life. It can determine what to do then
        if (controller.canBuildRobot(RobotType.POLITICIAN, direction, influence)) {
            controller.buildRobot(RobotType.POLITICIAN, direction, influence);
        }
    }
}
