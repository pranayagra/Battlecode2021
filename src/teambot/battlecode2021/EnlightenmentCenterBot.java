package teambot.battlecode2021;

import battlecode.common.*;
import mushroombot.battlecode2021.util.Communication;
import teambot.*;
import teambot.battlecode2021.util.*;

import java.util.*;

class EC_Information {

    MapLocation location;
    int health;
    int robotID;
    Team team;
    int roundFound;
    CommunicationLocation.FLAG_LOCATION_TYPES locationFlagType;

    public EC_Information(MapLocation location) {
        this.location = location;
        this.health = Integer.MAX_VALUE >> 2;
        this.robotID = Integer.MAX_VALUE >> 2;

    }

    public void setTeam(CommunicationLocation.FLAG_LOCATION_TYPES locationFlagType) {
        this.locationFlagType = locationFlagType;
        switch (locationFlagType) {
            case MY_EC_LOCATION:
                this.team = Cache.OUR_TEAM;
            case ENEMY_EC_LOCATION:
                this.team = Cache.OPPONENT_TEAM;
            default:
                this.team = Team.NEUTRAL;
        }
    }

    public void setRoundFound(int currentRound) {
        this.roundFound = currentRound;
    }

    @Override
    public String toString() {
        return "EC_Information{" +
                "location=" + location +
                ", health=" + health +
                ", robotID=" + robotID +
                ", team=" + team +
                ", roundFound=" + roundFound +
                ", locationFlagType=" + locationFlagType +
                '}';
    }
}


public class EnlightenmentCenterBot implements RunnableBot {
    private RobotController controller;

    private static MapLocation[] SCOUT_LOCATIONS;
    private static int SCOUT_LOCATIONS_CURRENT;

    /*
    private static int EC_ID_CURRENT_BRUTE_FORCE;
    private static int EC_ID_END_BRUTE_FORCE;

     */

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


    private static int[][] processRobotFlags;
    private static int[] processRobotFlagsSZ1D; //the size of the cols for each row

    private static int[] mapProcessRobotFlagsToIdx; //1 2 3 4 5=> 2 dies => 1 5 3 4 //list of free locations too?
    private static int processRobotFlagsSZ; //the size of the rows

    //Behavior: if does not exist, add to map. If exists, check type (neutral, friendly, enemy) and see if it has changed
    private static Map <MapLocation, EC_Information> foundECs;

    private static boolean enemyECInSensorRange = false;

    /*
    private static int num_validIDS;
    private static int[] validIDS;

    public static int num_ALL_MY_EC_LOCATIONs;
    public static int[] ALL_MY_EC_IDS;
    public static MapLocation[] ALL_MY_EC_LOCATIONS;

    public static int num_ALL_ENEMY_EC_LOCATIONs;
    public static MapLocation[] ALL_ENEMY_EC_LOCATIONS;

    private final int MAX_ECS_PER_TEAM = 3;
    */

    private static Random random;

    public EnlightenmentCenterBot(RobotController controller) throws GameActionException {
        this.controller = controller;
        init();
    }

    @Override
    public void init() throws GameActionException {
        random = new Random(controller.getID());

        SCOUT_LOCATIONS = new MapLocation[]{
                new MapLocation(-62,-62),
                new MapLocation(62, 62),
                new MapLocation(-62, 62),
                new MapLocation(62, -62),
                new MapLocation(-62, 0),
                new MapLocation(62, 0),
                new MapLocation(0, 62),
                new MapLocation(62, 0),
        };
        SCOUT_LOCATIONS_CURRENT = 0;


        /*
        EC_ID_CURRENT_BRUTE_FORCE = 10000;
        EC_ID_END_BRUTE_FORCE = EC_ID_CURRENT_BRUTE_FORCE + 4096;
        num_validIDS = 0;
        validIDS = new int[MAX_ECS_PER_TEAM * 4];

        num_ALL_MY_EC_LOCATIONs = 0;
        ALL_MY_EC_LOCATIONS = new MapLocation[MAX_ECS_PER_TEAM * 4];
        ALL_MY_EC_IDS = new int[MAX_ECS_PER_TEAM * 4];

        num_ALL_ENEMY_EC_LOCATIONs = 0;
        ALL_ENEMY_EC_LOCATIONS = new MapLocation[MAX_ECS_PER_TEAM * 4];

         */


        /* NEW STUFF BELOW */

        SCOUT_MUCKRAKER_IDs = new int[152];

        SLANDERER_IDs = new FastQueueSlanderers(152);
        POLITICIAN_DEFENDING_SLANDERER_IDs = new int[200];

        foundECs = new HashMap<>();

        for (RobotInfo robotInfo : controller.senseNearbyRobots(-1, Cache.OPPONENT_TEAM)) {
            if (robotInfo.type == RobotType.ENLIGHTENMENT_CENTER) {
                enemyECInSensorRange = true;
                break;
            }
        }

    }

    @Override
    public void turn() throws GameActionException {
        Debug.printRobotInformation();
        Debug.printMapInformation();

//        switch (controller.getRoundNum()) {
//            case 1:
//                round1();
//                return;
//            case 2:
//                defaultTurn();
//                round2();
//                break;
//            default:
//                defaultTurn();
//                break;
//        }

//        if (EC_ID_CURRENT_BRUTE_FORCE <= EC_ID_END_BRUTE_FORCE) {
//            brute_force_ids();
//        }

        defaultTurn();

        /*
        Debug.printInformation("NORTH -> ", Cache.MAP_TOP);
        Debug.printInformation("EAST -> ", Cache.MAP_RIGHT);
        Debug.printInformation("SOUTH -> ", Cache.MAP_BOTTOM);
        Debug.printInformation("WEST -> ", Cache.MAP_LEFT);
        Debug.printInformation("HEIGHT -> ", Cache.MAP_HEIGHT);
        Debug.printInformation("HEIGHT -> ", Cache.MAP_WIDTH);

        Debug.printInformation("CURRENT EC Information ", Arrays.asList(foundECs));
        */

//        Debug.printECInformation();

        Debug.printByteCode("END TURN BYTECODE => ");

    }

    /* LAZILY removes slanderer from the SLANDERER_IDs object 300 rounds after. It the ID is still valid, it is added to the Defensive Politician list
    * CAUTION: This list is not up to date if a slanderer is dies within 300 rounds of spawn due to enemy attack
    *  */
    public void slanderersToPoliticians() {
        if (!SLANDERER_IDs.isEmpty()) {
            if (controller.getRoundNum() - SLANDERER_IDs.getFrontCreationTime() >= 300) { //need to retire slanderer as 1) it got killed or 2) converted
                int id = SLANDERER_IDs.getFrontID();
                SLANDERER_IDs.removeFront();
                if (controller.canGetFlag(id) && POLITICIAN_DEFENDING_SLANDERER_SZ < POLITICIAN_DEFENDING_SLANDERER_IDs.length) {
                    POLITICIAN_DEFENDING_SLANDERER_IDs[POLITICIAN_DEFENDING_SLANDERER_SZ++] = id;
                }
            }
        }
        Debug.printInformation("updateSlanderers() => ", " VALID ");
    }

    private boolean parseCommsLocation(int encoding) {
        CommunicationLocation.FLAG_LOCATION_TYPES locationType = CommunicationLocation.decodeLocationType(encoding);
        MapLocation locationData = CommunicationLocation.decodeLocationData(encoding);

        switch (locationType) {
            case NORTH_MAP_LOCATION:
                Cache.MAP_TOP = locationData.y;
                break;
            case SOUTH_MAP_LOCATION:
                Cache.MAP_BOTTOM = locationData.y;
                break;
            case EAST_MAP_LOCATION:
                Cache.MAP_RIGHT = locationData.x;
                break;
            case WEST_MAP_LOCATION:
                Cache.MAP_LEFT = locationData.x;
                break;
            case MY_EC_LOCATION: //ADD STUFF HERE
            case ENEMY_EC_LOCATION:
            case NEUTRAL_EC_LOCATION:
                EC_Information EC_info = foundECs.get(locationData);
                if (EC_info == null) EC_info = new EC_Information(locationData);
                // update information
                EC_info.setRoundFound(controller.getRoundNum());
                EC_info.setTeam(locationType);
                foundECs.put(locationData, EC_info);
                break;
        }
        return true;
    }

    private boolean parseCommsRobotID(int encoding, MapLocation knownLocation) {
        CommunicationRobotID.COMMUNICATION_UNIT_TYPE communicatedUnitType = CommunicationRobotID.decodeCommunicatedUnitType(encoding);
        CommunicationRobotID.COMMUNICATION_UNIT_TEAM communicatedUnitTeam = CommunicationRobotID.decodeCommunicatedUnitTeam(encoding);
        int robotID = CommunicationRobotID.decodeRobotID(encoding);
        switch (communicatedUnitType) {
            case EC:
                if (knownLocation == null) return false;
                EC_Information EC_info = foundECs.get(knownLocation);
                if (EC_info == null) return false;
                EC_info.robotID = robotID;
                foundECs.put(knownLocation, EC_info);
                break;
            case SL:
                break;
            case PO:
                break;
            case MU:
                break;
        }
        return true;
    }

    private boolean parseECInfo(int encoding, MapLocation knownLocation) {
        if (knownLocation == null) return false;
        int ECHealth = CommunicationECInfo.decodeRobotHealth(encoding);
        CommunicationECInfo.COMMUNICATION_UNIT_TEAM ECTeam = CommunicationECInfo.decodeCommunicatedUnitTeam(encoding);
        EC_Information EC_info = foundECs.get(knownLocation);
        if (EC_info == null) return false;
        EC_info.health = ECHealth;
        return true;
    }

    public void parseFlag(int encoding, MapLocation knownLocation) {
        if (CommunicationLocation.decodeIsSchemaType(encoding)) {
            // LOCATION TYPE =>
            parseCommsLocation(encoding);
        } else if (CommunicationRobotID.decodeIsSchemaType(encoding)) {
            parseCommsRobotID(encoding, knownLocation);
        } else if (CommunicationECInfo.decodeIsSchemaType(encoding)) {
            parseECInfo(encoding, knownLocation);
        } else if (CommunicationMovement.decodeIsSchemaType(encoding)) {

        }

        Debug.printInformation("parseScoutFlag() => ", " VALID ");
    }

    // I have first few

    public void urgentFlagRecieved(int encoding) {

    }

    public void processValidFlag(int encoding) {

        if (Comms.decodeIsUrgent(encoding)) {
            urgentFlagRecieved(encoding);
            return;
        }

        if (Comms.decodeIsLastFlag(encoding)) {

            parseFlag(encoding, null);
            //execute all flags received here based (might be multiple)!


        } else {

            //ADD flag to the IDs set of flags
        }

    }

    /* Iterative over all friendly scout flags and parses the flag for the information. Assumes the list size will not go over 152 elements (risky)
    *
    *
    *  */
    public void readFriendlyScoutFlags() throws GameActionException {
        Debug.printInformation("readFriendlyScoutFlags() => ", " START ");
        for (int i = 0; i < SCOUT_MUCKRAKER_SZ; ++i) {

            if (controller.canGetFlag(SCOUT_MUCKRAKER_IDs[i])) {
                // read flag information
                int encoding = Comms.getFlag(SCOUT_MUCKRAKER_IDs[i]);
                processValidFlag(encoding);
            } else {
                // ID no longer exists at index i, (remove)
                //find first index at max size and go from there
                while (--SCOUT_MUCKRAKER_SZ >= i + 1) {
                    Debug.printInformation("readFriendlyScoutFlags() => " + SCOUT_MUCKRAKER_SZ, " trying to swap");
                    if (controller.canGetFlag(SCOUT_MUCKRAKER_IDs[SCOUT_MUCKRAKER_SZ])) {
                        Debug.printInformation("readFriendlyScoutFlags() => ", " START PARSE ");
                        SCOUT_MUCKRAKER_IDs[i] = SCOUT_MUCKRAKER_IDs[SCOUT_MUCKRAKER_SZ];
                        int encoding = Comms.getFlag(SCOUT_MUCKRAKER_IDs[i]);
                        processValidFlag(encoding);
                        Debug.printInformation("readFriendlyScoutFlags() => ", " FINISHED PARSE ");
                        break;
                    }
                }
            }
        }
        Debug.printInformation("readFriendlyScoutFlags() => ", " END ");
    }

    private void processAllECInformation() {

        for (Map.Entry<MapLocation, EC_Information> entry : foundECs.entrySet()) {

            MapLocation location = entry.getKey();
            EC_Information EC_info = entry.getValue();
            if (controller.getRoundNum() - 20 > EC_info.roundFound) {
                //its been 20 rounds since I found this...
                //send scout in direction to see what happened?
            }
        }

    }


    /*
    *
    *
    * */

    public void defaultTurn() throws GameActionException {
        slanderersToPoliticians();
        readFriendlyScoutFlags();

        int ran = random.nextInt(5);

        if (SCOUT_MUCKRAKER_SZ < 8) {
            MapLocation targetLocation = Cache.START_LOCATION.translate(SCOUT_LOCATIONS[SCOUT_LOCATIONS_CURRENT].x, SCOUT_LOCATIONS[SCOUT_LOCATIONS_CURRENT].y);
            Direction preferredDirection = Cache.START_LOCATION.directionTo(targetLocation);
            if (spawnScoutMuckraker(1, toBuildDirection(preferredDirection,4), targetLocation)) {
                SCOUT_LOCATIONS_CURRENT = (++SCOUT_LOCATIONS_CURRENT) % SCOUT_LOCATIONS.length;
                Debug.printInformation("Spawned Scout => ", targetLocation);
            }
        } else if (SLANDERER_IDs.getSize() < 8) {
                spawnLatticeSlanderer((int) (controller.getInfluence() * 0.1), randomValidDirection());
                Debug.printInformation("Spawned Slanderer => ", "VALID");
        } else if (ran <= 2) {
                spawnDefendingPolitician(15, randomValidDirection(), null);
                Debug.printInformation("spawnDefendingPolitician => ", "VALID");
        } else if (ran <= 3) {
                spawnLatticeSlanderer((int) (controller.getInfluence() * 0.1), randomValidDirection());
                Debug.printInformation("spawnLatticeSlanderer => ", "VALID");
        } else if (ran == 4) {
                spawnWallMuckraker(2, randomValidDirection(), null);
                Debug.printInformation("spawnWallMuckraker => ", "VALID");
        }

        processAllECInformation();
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

    private boolean setFlagForSpawnedUnit(Direction direction, CommunicationECSpawnFlag.ACTION actionType, CommunicationECSpawnFlag.SAFE_QUADRANT safeQuadrant, MapLocation locationData) throws GameActionException {
        int flag = CommunicationECSpawnFlag.encodeSpawnInfo(direction, actionType, safeQuadrant, locationData);
        if (!Comms.canScheduleFlag(controller.getRoundNum()+1)) {
            Debug.printInformation("Warning - Potential Schedule Conflict at turn", controller.getRoundNum()+1);
            return false;
        }
        if (Comms.scheduleFlag(controller.getRoundNum()+1, flag)) {
            return true;
        }
        return false;
    }

    private MapLocation spawnLocationNull() {
//        return Cache.CURRENT_LOCATION;
        return Pathfinding.randomLocation();
    }

    private boolean spawnScoutMuckraker(int influence, Direction direction, MapLocation location) throws GameActionException {
        //TODO: should spawn muckraker in location

        if (location == null) location = spawnLocationNull();

        // Only build if message can be sent out
        if (direction != null && controller.canBuildRobot(RobotType.MUCKRAKER, direction, influence)) {
            if (setFlagForSpawnedUnit(direction, CommunicationECSpawnFlag.ACTION.SCOUT_LOCATION, CommunicationECSpawnFlag.SAFE_QUADRANT.NORTH_EAST, location)) {
                controller.buildRobot(RobotType.MUCKRAKER, direction, influence);
                SCOUT_MUCKRAKER_IDs[SCOUT_MUCKRAKER_SZ++] = controller.senseRobotAtLocation(Cache.CURRENT_LOCATION.add(direction)).ID;
                return true;
            }
        }
        return false;

    }

    private void spawnWallMuckraker(int influence, Direction direction, MapLocation location) throws GameActionException {
        //TODO: should spawn muckrakers to build wall
        if (location == null) location = spawnLocationNull();

        if (direction != null && controller.canBuildRobot(RobotType.MUCKRAKER, direction, influence)) {
            controller.buildRobot(RobotType.MUCKRAKER, direction, influence);
            setFlagForSpawnedUnit(direction, CommunicationECSpawnFlag.ACTION.DEFEND_LOCATION, CommunicationECSpawnFlag.SAFE_QUADRANT.NORTH_EAST, location);
        }
    }

    private void spawnLatticeSlanderer(int influence, Direction direction) throws GameActionException {
        //TODO: should spawn slanderer, which default behavior is to build lattice
        if (direction != null && controller.canBuildRobot(RobotType.SLANDERER, direction, influence)) {
            controller.buildRobot(RobotType.SLANDERER, direction, influence);
            Debug.printInformation("PUSHING TO SLANDERER ", " BEFORE");
            SLANDERER_IDs.push(controller.senseRobotAtLocation(Cache.CURRENT_LOCATION.add(direction)).ID, controller.getRoundNum());
            Debug.printInformation("PUSHING TO SLANDERER ", " AFTER");
        }
    }

    private void spawnDefendingPolitician(int influence, Direction direction, MapLocation location) throws GameActionException {
        //TODO: should defend slanderers outside the muckrakers wall
        if (location == null) location = spawnLocationNull();
        if (direction != null && controller.canBuildRobot(RobotType.POLITICIAN, direction, influence)) {
            controller.buildRobot(RobotType.POLITICIAN, direction, influence);
            setFlagForSpawnedUnit(direction, CommunicationECSpawnFlag.ACTION.DEFEND_LOCATION, CommunicationECSpawnFlag.SAFE_QUADRANT.NORTH_EAST, location);
        }
    }

    private void spawnAttackingPolitician(int influence, Direction direction, MapLocation location) throws GameActionException {
        //TODO: should only be called if the politician is meant to attack some base -> need to create politician with enough influence, set my EC flag to the location + attacking poli
        //Assumption: politician upon creation should read EC flag and know it's purpose in life. It can determine what to do then
        if (location == null) location = spawnLocationNull();
        if (direction != null && controller.canBuildRobot(RobotType.POLITICIAN, direction, influence)) {
            controller.buildRobot(RobotType.POLITICIAN, direction, influence);
            setFlagForSpawnedUnit(direction, CommunicationECSpawnFlag.ACTION.ATTACK_LOCATION, CommunicationECSpawnFlag.SAFE_QUADRANT.NORTH_EAST, location);
        }
    }

    /*
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
    } */
}
