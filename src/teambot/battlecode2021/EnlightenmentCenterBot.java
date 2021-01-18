package teambot.battlecode2021;

import battlecode.common.*;
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

    public EC_Information(MapLocation location, int health, int robotID, int currentRound, CommunicationLocation.FLAG_LOCATION_TYPES locationFlagType) {
        this.location = location;
        this.health = health;
        this.robotID = robotID;
        this.roundFound = currentRound;
        setTeam(locationFlagType);
    }

    public void setTeam(CommunicationLocation.FLAG_LOCATION_TYPES locationFlagType) {
        this.locationFlagType = locationFlagType;
        switch (locationFlagType) {
            case MY_EC_LOCATION:
                this.team = Cache.OUR_TEAM;
                break;
            case ENEMY_EC_LOCATION:
                this.team = Cache.OPPONENT_TEAM;
                break;
            default:
                this.team = Team.NEUTRAL;
                break;
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

enum robotSpawnType {
    ATTACKING_PO,
    DEFENDING_PO,
    SCOUT_MUCK,
    SLANDERER,
}

class BotRequest {
    robotSpawnType type;
    int influence;
    MapLocation location;
    Team team;

    public BotRequest (robotSpawnType type, int influence, MapLocation location, Team team) {
        this.type = type;
        this.influence = influence;
        this.location = location;
        this.team = team;
    }

}


public class EnlightenmentCenterBot implements RunnableBot {
    private RobotController controller;

    private static int[] enemyDirectionCounts; //8 values indicating how dangerous a side of the map is (used in spawning politicians/scouting)
    private static int[] wallDirectionReward; // 8 values for how close the wall is from a certain direction (used in spawning slanderers in conjuction to enemyDirectionCounts)
    private static int[] numSlanderersWallDirectionSpawned;
    private static final int CEIL_MAX_REWARD = 40;
    private static boolean hasFoundEnemies;

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

    /* Do we have one guide broadcasting information to newly created units */
    private static int GUIDE_ID = 0;

    /* Should always iterate on all of these -- highest priority (should be at most 100) */
    private static int SCOUT_MUCKRAKER_SZ;
    private static int[] SCOUT_MUCKRAKER_IDs;

    //TODO: need to think of building requests when we cannot build on a certain round or have too many requests.. we probably want some type of priority here
    private static BotRequest[] buildRequests; //want to store -> the necessary information (type, influence, direction, deltaDirectionAllowed) and how old the request is?
    private static int buildRequestsSZ;

    private static int[][] processRobotIDandFlags; // [#robots][#flags]. [i][0]=robotID, i[1,2,3]=flagType
    private static int[] numFlagsSavedForARobot; //the size of the cols for each row
    private static int numRobotsToProcessFlags; //the size of the rows

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

    private static MapLocation attackNeutralLocation;
    private static int attackNeutralLocationHealth;
    private static int attackNeutralLocationAge;
    private static boolean deployedPoliticianToAttackNeutral;

    private static MapLocation attackEnemyLocation;
    private static int attackEnemyLocationHealth;
    private static int attackEnemyLocationAge;
    private static boolean deployedPoliticianToAttackEnemy;

    private static MapLocation deployScoutToECLocation;


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

        enemyDirectionCounts = new int[8];
        wallDirectionReward = new int[8];
        numSlanderersWallDirectionSpawned = new int[8];

        buildRequests = new BotRequest[50];

        /* PROCESS FLAGS THROUGH MULTIPLE ROUNDS */
        processRobotIDandFlags = new int[100][4]; //robotID ToQuery, flag1, flag2, flag3
        numFlagsSavedForARobot = new int[100]; //total number of flags for a robot (on spawn=1 for robotID, then 2-4), reset if last flag
        numRobotsToProcessFlags = 0; //total number of robots to query (add +1 on spawn)


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

        naiveBid();

        numRobotsToProcessFlags %= processRobotIDandFlags.length;
        defaultTurn();

        Debug.printInformation("CURRENT EC Information ", Arrays.asList(foundECs));
        Debug.printInformation("WALL REWARDS: ", Arrays.toString(wallDirectionReward));
        Debug.printInformation("NUM SLANDERERS: ", Arrays.toString(numSlanderersWallDirectionSpawned));
        Debug.printInformation("ENEMY DANGERS: ", Arrays.toString(enemyDirectionCounts));

        Debug.printByteCode("EC END TURN => ");

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

    //TODO (IMPORTANT): add values to wallDirectionDistance
    private void updateWallDistance() {
        //for all 8 directions calculate distance
        //for direction, compiute distance. for midway, compute average. use as heurstic!

        if (Cache.MAP_TOP != 0) {
            wallDirectionReward[0] = CEIL_MAX_REWARD - (Cache.MAP_TOP - Cache.CURRENT_LOCATION.y);
        }
        if (Cache.MAP_RIGHT != 0) {
            wallDirectionReward[2] = CEIL_MAX_REWARD - (Cache.MAP_RIGHT - Cache.CURRENT_LOCATION.x);
        }
        if (Cache.MAP_BOTTOM != 0) {
            wallDirectionReward[4] = CEIL_MAX_REWARD - (Cache.CURRENT_LOCATION.y - Cache.MAP_BOTTOM);
        }
        if (Cache.MAP_LEFT != 0) {
            wallDirectionReward[6] = CEIL_MAX_REWARD - (Cache.CURRENT_LOCATION.x - Cache.MAP_LEFT);
        }
        if (Cache.MAP_TOP != 0 && Cache.MAP_RIGHT != 0) {
            wallDirectionReward[1] = (wallDirectionReward[0] + wallDirectionReward[2])/2;
        }
        if (Cache.MAP_RIGHT != 0 && Cache.MAP_BOTTOM != 0) {
            wallDirectionReward[3] = (wallDirectionReward[2] + wallDirectionReward[4])/2;
        }
        if (Cache.MAP_BOTTOM != 0 && Cache.MAP_LEFT != 0) {
            wallDirectionReward[5] = (wallDirectionReward[4] + wallDirectionReward[6])/2;
        }
        if (Cache.MAP_LEFT != 0 && Cache.MAP_TOP != 0) {
            wallDirectionReward[7] = (wallDirectionReward[6] + wallDirectionReward[0])/2;
        }
    }

    private boolean parseCommsLocation(int encoding) throws GameActionException {
        CommunicationLocation.FLAG_LOCATION_TYPES locationType = CommunicationLocation.decodeLocationType(encoding);
        MapLocation locationData = CommunicationLocation.decodeLocationData(encoding);

        switch (locationType) {
            case NORTH_MAP_LOCATION:
                if (Cache.MAP_TOP == 0) { 
                    Comms.checkAndAddFlag(encoding);
                    Cache.MAP_TOP = locationData.y;
                    updateWallDistance();
                }
                break;
            case SOUTH_MAP_LOCATION:
                if (Cache.MAP_BOTTOM == 0) { 
                    Comms.checkAndAddFlag(encoding);
                    Cache.MAP_BOTTOM = locationData.y;
                    updateWallDistance();
                }
                break;
            case EAST_MAP_LOCATION:
                if (Cache.MAP_RIGHT == 0) { 
                    Comms.checkAndAddFlag(encoding);
                    Cache.MAP_RIGHT = locationData.x;
                    updateWallDistance();
                }
                break;
            case WEST_MAP_LOCATION:
                if (Cache.MAP_LEFT == 0) { 
                    Comms.checkAndAddFlag(encoding);
                    Cache.MAP_LEFT = locationData.x;
                    updateWallDistance();
                }
                break;
        }

        return true;
    }

    private boolean parseCommsMovement(int encoding) {
        CommunicationMovement.MY_UNIT_TYPE myUnitType = CommunicationMovement.decodeMyUnitType(encoding);
        CommunicationMovement.MOVEMENT_BOTS_DATA movementBotData = CommunicationMovement.decodeMyPreferredMovement(encoding);
        CommunicationMovement.COMMUNICATION_TO_OTHER_BOTS communicationToOtherBots = CommunicationMovement.decodeCommunicationToOtherBots(encoding);
        int amount = CommunicationMovement.decodeDangerDirections(encoding) + 1;
        int direction = CommunicationMovement.convert_MovementBotData_DirectionInt(movementBotData);
        if (communicationToOtherBots == CommunicationMovement.COMMUNICATION_TO_OTHER_BOTS.SPOTTED_ENEMY_UNIT) {
            // WE SPOTTED AN ENEMY AT Direction movementBotData (EC => enemy location)
            Debug.printInformation("ADDING DANGER" + direction, amount);
            enemyDirectionCounts[direction] += amount;
            enemyDirectionCounts[(direction + 7) % 8] += amount/2;
            enemyDirectionCounts[(direction + 1) % 8] += amount/2;
            hasFoundEnemies = true;
        } else if (communicationToOtherBots == CommunicationMovement.COMMUNICATION_TO_OTHER_BOTS.SEND_DEFENDING_POLITICIANS) {
            //TODO: send politician to defend at direction location
        }

        return true;
    }

    /* We will never parse ECRobotID by itself -- this is purely to communicate moveable type bots. Not used currently anywhere */
    private boolean parseCommsRobotID(int encoding) {
        Debug.printInformation("USED UNIMPLEMENTED METHOD parseCommsRobotID() ", " ERROR");
        CommunicationRobotID.COMMUNICATION_UNIT_TYPE communicatedUnitType = CommunicationRobotID.decodeCommunicatedUnitType(encoding);
        CommunicationRobotID.COMMUNICATION_UNIT_TEAM communicatedUnitTeam = CommunicationRobotID.decodeCommunicatedUnitTeam(encoding);
        int robotID = CommunicationRobotID.decodeRobotID(encoding);
        switch (communicatedUnitType) {
            case SL:
                break;
            case PO:
                break;
            case MU:
                break;
        }
        return true;
    }

    public void urgentFlagRecieved(int encoding) throws GameActionException {
        if (CommunicationLocation.decodeIsSchemaType(encoding)) {
            parseCommsLocation(encoding);
        } else if (CommunicationMovement.decodeIsSchemaType(encoding)) {
            parseCommsMovement(encoding);
        } else if (CommunicationRobotID.decodeIsSchemaType(encoding)) {
            parseCommsRobotID(encoding);
        } else if (CommunicationECInfo.decodeIsSchemaType(encoding)) {
            Debug.printInformation("UNIMPLEMENTED PARSER ECINFO", " ERROR");
        }
    }

    public void processRobotFlag(int robotIDX, int oneMoreThanMessageSize) throws GameActionException {

        if (oneMoreThanMessageSize == 2) {
            // this is a single message => simple case
            int flag1 = processRobotIDandFlags[robotIDX][1];
            if (CommunicationLocation.decodeIsSchemaType(flag1)) {
                parseCommsLocation(flag1);
            } else if (CommunicationMovement.decodeIsSchemaType(flag1)) {
                parseCommsMovement(flag1);
            } else if (CommunicationRobotID.decodeIsSchemaType(flag1)) {
                parseCommsRobotID(flag1);
            } else if (CommunicationECInfo.decodeIsSchemaType(flag1)) {
                Debug.printInformation("UNIMPLEMENTED PARSER ECINFO", " ERROR");
            }
        } else if (oneMoreThanMessageSize == 3) {
            // this is two messages => has no use case right now (should never happen)
            Debug.printInformation("UNIMPLEMENTED PARSER 2 MESSAGES", " ERROR");
        } else if (oneMoreThanMessageSize == 4) {
            // this is three messages => most likely Location + ECInfo + RobotID
            int flag1 = processRobotIDandFlags[robotIDX][1];
            int flag2 = processRobotIDandFlags[robotIDX][2];
            int flag3 = processRobotIDandFlags[robotIDX][3];
            if (CommunicationLocation.decodeIsSchemaType(flag1) &&
                    CommunicationECInfo.decodeIsSchemaType(flag2) &&
                    CommunicationRobotID.decodeIsSchemaType(flag3)) {
                MapLocation ECLocation = CommunicationLocation.decodeLocationData(flag1);
                CommunicationLocation.FLAG_LOCATION_TYPES ECTeam = CommunicationLocation.decodeLocationType(flag1);
                int ECHealth = CommunicationECInfo.decodeRobotHealth(flag2);
                int ECRobotID = CommunicationRobotID.decodeRobotID(flag3);
                EC_Information ecInfo = new EC_Information(ECLocation, ECHealth, ECRobotID, controller.getRoundNum(), ECTeam);
                foundECs.put(ECLocation, ecInfo);
                Debug.printInformation("EC Received ECScoutInformation ", ecInfo.toString());
            }
            //NOTE -> REMEMBER THAT IF WE HAVE OTHER TYPES OF MULTI-ROUND FLAGS, WE WILL NEED TO ADD CONDITIONS HERE... CURRENTLY "HARDCODED"
        }

        // The robot is still of value (do not remove from system), but we must reset the size of the current message (1 because first index is robotID)
        numFlagsSavedForARobot[robotIDX] = 1;

    }

    /* Iterative over all friendly scout flags and parses the flag for the information. Assumes the list size will not go over 152 elements (risky)
    *
    *
    *  */
    public void iterateAllUnitIDs() throws GameActionException {

        for (int i = 0; i < numRobotsToProcessFlags; ++i) { //iterate through all flags
            int robotID = processRobotIDandFlags[i][0]; //the robotID of the corresponding idx
//            System.out.println("");
            if (controller.canGetFlag(robotID)) { //this robot is still alive
                //query flag information, check isUrgent, isLastFlag bit, or add to list of flags)
                int encodedFlag = controller.getFlag(robotID);
                if (encodedFlag == 0) continue;
//                System.out.println("robotID -> " + robotID + " " + encodedFlag);
                if (Comms.decodeIsUrgent(encodedFlag)) { //skip queue (this flag is urgent and interrupting the queued message)
                    urgentFlagRecieved(encodedFlag);
                } else if (Comms.decodeIsLastFlag(encodedFlag)) { //if the flag is the last flag in sequence, let's add it and then process all the flags
                    processRobotIDandFlags[i][numFlagsSavedForARobot[i]++] = encodedFlag;
                    processRobotFlag(i, numFlagsSavedForARobot[i]);
                } else { //let us add to the list at index i and size of message so far. increment size of message
//                    System.out.println("WTF ->" + numFlagsSavedForARobot[i]);
                    processRobotIDandFlags[i][numFlagsSavedForARobot[i]++] = encodedFlag;
                }
            } else { //the robot has died.
                //swap with last existing robot & then perform data update
                while (--numRobotsToProcessFlags >= i + 1) {
                    int robotIDNew = processRobotIDandFlags[numRobotsToProcessFlags][0];
                    if (controller.canGetFlag(robotIDNew)) { //found swapping element
                        //swap the data (robotID + flags) and set the size?
                        for (int swapData = 0; swapData < numFlagsSavedForARobot[numRobotsToProcessFlags]; ++swapData) {
                            processRobotIDandFlags[i][swapData] = processRobotIDandFlags[numRobotsToProcessFlags][swapData];
                        }
                        robotID = processRobotIDandFlags[i][0];
                        numFlagsSavedForARobot[i] = numFlagsSavedForARobot[numRobotsToProcessFlags];
                        int encodedFlag = controller.getFlag(robotID);
                        if (encodedFlag == 0) break;
                        if (Comms.decodeIsUrgent(encodedFlag)) { //skip queue (this flag is urgent and interrupting the queued message)
                            urgentFlagRecieved(encodedFlag);
                        } else if (Comms.decodeIsLastFlag(encodedFlag)) { //if the flag is the last flag in sequence, let's add it and then process all the flags
                            processRobotIDandFlags[i][numFlagsSavedForARobot[i]++] = encodedFlag;
                            processRobotFlag(i, numFlagsSavedForARobot[i]);
                        } else { //let us add to the list at index i and size of message so far. increment size of message
                            processRobotIDandFlags[i][numFlagsSavedForARobot[i]++] = encodedFlag;
                        }
                        break;
                    }
                }
            }
        }

    }

    private void processAllECInformation() {

        MapLocation neutralLocation = null;
        int minNeutralECHealth = 9999999;

        MapLocation enemyLocation = null;
        int minEnemyECHealth = 9999999;

        for (Map.Entry<MapLocation, EC_Information> entry : foundECs.entrySet()) {

            MapLocation location = entry.getKey();
            EC_Information ECInfo = entry.getValue();
            if (controller.getRoundNum() - 20 > ECInfo.roundFound) {
                //its been 20 rounds since I found this...
                //send scout in direction to see what happened?
                deployScoutToECLocation = location;
            }

            if (ECInfo.health < minNeutralECHealth && ECInfo.team.equals(Team.NEUTRAL)) {
                minNeutralECHealth = ECInfo.health;
                neutralLocation = location;
            }

            if (ECInfo.health < minEnemyECHealth && ECInfo.team.equals(Cache.OPPONENT_TEAM)) {
                minEnemyECHealth = ECInfo.health;
                enemyLocation = location;
            }
        }

        attackEnemyLocation = enemyLocation; attackEnemyLocationHealth = minEnemyECHealth; attackEnemyLocationAge = controller.getRoundNum();
        attackNeutralLocation = neutralLocation; attackNeutralLocationHealth = minNeutralECHealth; attackNeutralLocationAge = controller.getRoundNum();

        Debug.printInformation("BEST NEUTRAL INFO: " + neutralLocation, minNeutralECHealth);
        Debug.printInformation("BEST ENEMY INFO: " + enemyLocation, minEnemyECHealth);

    }


    private Direction checkSpawnSlanderers() {
        int bestWallDir = -1;
        int bestReward = -999999;

        //logic -> if no walls have been found and an enemy is found in dir, we can spawn.
        //      if wall has been found we spawn in direction
        //      if both found we perform some combination of both

        boolean noWallsFound = false;
        if (Cache.MAP_TOP == 0 && Cache.MAP_RIGHT == 0 && Cache.MAP_BOTTOM == 0 && Cache.MAP_LEFT == 0) noWallsFound = true;

        if (noWallsFound && hasFoundEnemies) {
            //spawn away from enemies
            for (int i = 0; i < 8; ++i) {
                int totalReward = -enemyDirectionCounts[i];
                if (bestReward < totalReward && enemyDirectionCounts[i] <= 1 * controller.getRoundNum()/2) {
                    bestReward = totalReward;
                    bestWallDir = i;
                }
            }
        } else if (!noWallsFound) { //found walls
            for (int i = 0; i < 8; ++i) {
                int totalReward = wallDirectionReward[i] - numSlanderersWallDirectionSpawned[i] - enemyDirectionCounts[i];
                if (bestReward < totalReward && wallDirectionReward[i] >= 5 && enemyDirectionCounts[i] <= 1 * controller.getRoundNum()/2) {
                    bestReward = totalReward;
                    bestWallDir = i;
                }
            }
        }

        if (bestWallDir != -1) {
            Direction toBuild = toBuildDirection(Direction.values()[bestWallDir], 1);
            return toBuild;
        }

        return null;
    }

    private Direction checkSpawnDefenders() {
        int bestDangerDir = -1;
        int bestDanger = 0;

        for (int i = 0; i < 8; ++i) {
            if (enemyDirectionCounts[i] > bestDanger) {
                bestDanger = enemyDirectionCounts[i];
                bestDangerDir = i;
            }
        }

        if (bestDangerDir != -1) {
            return Direction.values()[bestDangerDir];
        }

        return null;

    }

    /*
    *
    *
    * */

    public void defaultTurn() throws GameActionException {
        slanderersToPoliticians();
        iterateAllUnitIDs();
        processAllECInformation();
        updateWallDistance();

        int ran = random.nextInt(5);

        // IF NO INFLUENCE, SPAWN MUCKRAKER
        if (controller.getInfluence() <= 15) {
            spawnScoutMuckraker(1, randomValidDirection(), null);
            return;
        }

        //ATTACK NEUTRAL EC

        if (attackNeutralLocationHealth != 9999999 && attackNeutralLocationHealth >= controller.getInfluence()) {
            buildRequests[buildRequestsSZ++] = new BotRequest(robotSpawnType.ATTACKING_PO, attackNeutralLocationHealth / 3 + 15, attackNeutralLocation, Team.NEUTRAL);
            buildRequests[buildRequestsSZ++] = new BotRequest(robotSpawnType.ATTACKING_PO, attackNeutralLocationHealth / 3 + 15, attackNeutralLocation, Team.NEUTRAL);
            buildRequests[buildRequestsSZ++] = new BotRequest(robotSpawnType.ATTACKING_PO, attackNeutralLocationHealth / 3 + 15, attackNeutralLocation, Team.NEUTRAL);
            foundECs.remove(attackNeutralLocation);
        }

        //ATTACK ENEMY EC
        if (attackEnemyLocationHealth != 9999999 && attackEnemyLocationHealth >= controller.getInfluence() && controller.getRoundNum() - attackEnemyLocationAge <= 25) {
            buildRequests[buildRequestsSZ++] = new BotRequest(robotSpawnType.ATTACKING_PO, attackNeutralLocationHealth / 5 + 25, attackNeutralLocation, Cache.OPPONENT_TEAM);
            buildRequests[buildRequestsSZ++] = new BotRequest(robotSpawnType.ATTACKING_PO, attackNeutralLocationHealth / 5 + 25, attackNeutralLocation, Cache.OPPONENT_TEAM);
            buildRequests[buildRequestsSZ++] = new BotRequest(robotSpawnType.ATTACKING_PO, attackNeutralLocationHealth / 5 + 25, attackNeutralLocation, Cache.OPPONENT_TEAM);
            buildRequests[buildRequestsSZ++] = new BotRequest(robotSpawnType.ATTACKING_PO, attackNeutralLocationHealth / 5 + 25, attackNeutralLocation, Cache.OPPONENT_TEAM);
            buildRequests[buildRequestsSZ++] = new BotRequest(robotSpawnType.ATTACKING_PO, attackNeutralLocationHealth / 5 + 25, attackNeutralLocation, Cache.OPPONENT_TEAM);
            foundECs.remove(attackEnemyLocation);
        }

        if (buildRequestsSZ > 0) {
            BotRequest botRequest = buildRequests[0];
            if (botRequest.team.equals(Team.NEUTRAL)) {
                if (botRequest.influence + 15 > controller.getInfluence()) {
                    spawnAttackingPolitician(botRequest.influence, toBuildDirection(Cache.START_LOCATION.directionTo(botRequest.location), 4), botRequest.location, Team.NEUTRAL);
                }
            } else {
                if (botRequest.influence + 15 > controller.getInfluence()) {
                    spawnAttackingPolitician(botRequest.influence, toBuildDirection(Cache.START_LOCATION.directionTo(botRequest.location), 4), botRequest.location, Cache.OPPONENT_TEAM);
                }
            }
        }

        //ROUND 1 STRAT
        if (controller.getRoundNum() == 1) {
            if (Cache.ALL_NEARBY_ENEMY_ROBOTS.length > 0) {
                //rush strat here?
                spawnScoutMuckraker(1, randomValidDirection(), null);
            } else {
                //econ strat start
                spawnLatticeSlanderer(130, randomValidDirection());
            }
            return;
        }

        // FIND SAFEST AND DANGEREST DIRECTIONS
        Direction safeDirection = checkSpawnSlanderers();
        Direction dangerDirection = checkSpawnDefenders();

        Debug.printInformation("SAFE DIRECTION " + safeDirection + " and DANGER DIRECTION " + dangerDirection, " INFO");

        int slandererSpawn = random.nextInt(10) + 1; //1-10
        //NOTE: on spawn both safeDirection and dangerDirection will be null, so we  will inheritately spawn scouts first
        if (safeDirection != null && slandererSpawn <= 8 && SLANDERER_IDs.getSize() <= 12) { //80% of time spawn slanderer in safe direction
            spawnLatticeSlanderer((int) (controller.getInfluence() * 0.6), safeDirection);
        } else if (dangerDirection != null && POLITICIAN_DEFENDING_SLANDERER_SZ <= 6) { //20% of time spawn politician in safe direction
            int influenceSpend = Math.min(Math.max(21, (int)(controller.getInfluence() * 0.2)), 35);
            spawnDefendingPolitician(influenceSpend, dangerDirection,null);
        }

        if (!controller.canGetFlag(GUIDE_ID)) {
            spawnGuideMuckraker(1, randomValidDirection());
        }

        int randomInt = random.nextInt(10) + 1;
        if (randomInt <= 9 && SCOUT_MUCKRAKER_SZ < 8) {
            MapLocation targetLocation = Cache.START_LOCATION.translate(SCOUT_LOCATIONS[SCOUT_LOCATIONS_CURRENT].x, SCOUT_LOCATIONS[SCOUT_LOCATIONS_CURRENT].y);
            Direction preferredDirection = Cache.START_LOCATION.directionTo(targetLocation);
            if (spawnScoutMuckraker(1, toBuildDirection(preferredDirection,4), targetLocation)) {
                SCOUT_LOCATIONS_CURRENT = (++SCOUT_LOCATIONS_CURRENT) % SCOUT_LOCATIONS.length;
                Debug.printInformation("Spawned Scout => ", targetLocation);
            }
        } else if (randomInt == 10 && POLITICIAN_DEFENDING_SLANDERER_SZ <= 10) {
            Direction dir = randomValidDirection();
            if (dangerDirection != null) dir = dangerDirection;
            int influenceSpend = Math.min(Math.max(21, (int)(controller.getInfluence() * 0.2)), 35);
            spawnDefendingPolitician(influenceSpend, toBuildDirection(dir,2),null);
        }

        randomInt = random.nextInt(10) + 1;

        if (randomInt <= 5) {
            spawnScoutMuckraker(1, randomValidDirection(), null);
        } else if (randomInt <= 7) {
            Direction dir = randomValidDirection();
            if (dangerDirection != null) dir = dangerDirection;
            int influenceSpend = Math.min(Math.max(21, (int)(controller.getInfluence() * 0.2)), 35);
            spawnDefendingPolitician(influenceSpend, toBuildDirection(dir, 2), null);
        } else {
            spawnLatticeSlanderer((int) (controller.getInfluence() * 0.65), safeDirection);
        }

    }

    /* Simple bidding strategy */ 

    private void naiveBid() throws GameActionException {
        int bid = Math.max(1,controller.getInfluence() / 100);
        if (controller.canBid(bid)){
            controller.bid(bid);
        }
    }

    /* Greedily returns the closest valid direction to preferredDirection within the directionFlexibilityDelta value (2 means allow for 2 clockwise 45 deg in both directions)
    Returns null if no valid direction with specification
    directionFlexibilityDelta: max value 4 */
    private Direction toBuildDirection(Direction preferredDirection, int directionFlexibilityDelta) {

        if (!controller.isReady() || preferredDirection == null) return null;

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
                processRobotIDandFlags[numRobotsToProcessFlags][0] = controller.senseRobotAtLocation(Cache.CURRENT_LOCATION.add(direction)).ID;
                numFlagsSavedForARobot[numRobotsToProcessFlags] = 1;
                numRobotsToProcessFlags++;
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

    private void spawnGuideMuckraker(int influence, Direction direction) throws GameActionException {

        if (direction != null && controller.canBuildRobot(RobotType.MUCKRAKER, direction, influence)) {
            controller.buildRobot(RobotType.MUCKRAKER, direction, influence);
            setFlagForSpawnedUnit(direction, CommunicationECSpawnFlag.ACTION.DEFEND_LOCATION, CommunicationECSpawnFlag.SAFE_QUADRANT.NORTH_EAST, Cache.CURRENT_LOCATION);
            GUIDE_ID = controller.senseRobotAtLocation(Cache.CURRENT_LOCATION.add(direction)).ID;
            if (Cache.MAP_TOP != 0) {
                int flag = CommunicationLocation.encodeLOCATION(
                    false, true, CommunicationLocation.FLAG_LOCATION_TYPES.NORTH_MAP_LOCATION, 
                    new MapLocation(Cache.CURRENT_LOCATION.x,Cache.MAP_TOP));
                Comms.checkAndAddFlag(flag);
            }
            if (Cache.MAP_RIGHT != 0) {
                int flag = CommunicationLocation.encodeLOCATION(
                    false, true, CommunicationLocation.FLAG_LOCATION_TYPES.EAST_MAP_LOCATION, 
                    new MapLocation(Cache.MAP_RIGHT,Cache.CURRENT_LOCATION.y));
                Comms.checkAndAddFlag(flag);
            }
            if (Cache.MAP_BOTTOM != 0) {
                int flag = CommunicationLocation.encodeLOCATION(
                    false, true, CommunicationLocation.FLAG_LOCATION_TYPES.SOUTH_MAP_LOCATION, 
                    new MapLocation(Cache.CURRENT_LOCATION.x,Cache.MAP_BOTTOM));
                Comms.checkAndAddFlag(flag);
            }
            if (Cache.MAP_LEFT != 0) {
                int flag = CommunicationLocation.encodeLOCATION(
                    false, true, CommunicationLocation.FLAG_LOCATION_TYPES.WEST_MAP_LOCATION, 
                    new MapLocation(Cache.MAP_LEFT,Cache.CURRENT_LOCATION.y));
                Comms.checkAndAddFlag(flag);
            }
            processRobotIDandFlags[numRobotsToProcessFlags][0] = GUIDE_ID;
            numFlagsSavedForARobot[numRobotsToProcessFlags] = 1;
            numRobotsToProcessFlags++;
        }
    }

    private void spawnLatticeSlanderer(int influence, Direction direction) throws GameActionException {
        //TODO: should spawn slanderer, which default behavior is to build lattice
        if (direction != null && controller.canBuildRobot(RobotType.SLANDERER, direction, influence)) {
            controller.buildRobot(RobotType.SLANDERER, direction, influence);
            Debug.printInformation("PUSHING TO SLANDERER ", " BEFORE");
            numSlanderersWallDirectionSpawned[direction.ordinal()]++;
            SLANDERER_IDs.push(controller.senseRobotAtLocation(Cache.CURRENT_LOCATION.add(direction)).ID, controller.getRoundNum());
            Debug.printInformation("PUSHING TO SLANDERER ", " AFTER");
        }
    }

    private void spawnDefendingPolitician(int influence, Direction direction, MapLocation location) throws GameActionException {
        //TODO: should defend slanderers outside the muckrakers wall
        if (location == null) location = spawnLocationNull();
        if (direction != null && controller.canBuildRobot(RobotType.POLITICIAN, direction, influence)) {
            controller.buildRobot(RobotType.POLITICIAN, direction, influence);
            processRobotIDandFlags[numRobotsToProcessFlags][0] = controller.senseRobotAtLocation(Cache.CURRENT_LOCATION.add(direction)).ID;
            numFlagsSavedForARobot[numRobotsToProcessFlags] = 1;
            numRobotsToProcessFlags++;
            setFlagForSpawnedUnit(direction, CommunicationECSpawnFlag.ACTION.DEFEND_LOCATION, CommunicationECSpawnFlag.SAFE_QUADRANT.NORTH_EAST, location);
        }
    }

    private void spawnAttackingPolitician(int influence, Direction direction, MapLocation location, Team team) throws GameActionException {
        //TODO: should only be called if the politician is meant to attack some base -> need to create politician with enough influence, set my EC flag to the location + attacking poli
        //Assumption: politician upon creation should read EC flag and know it's purpose in life. It can determine what to do then
        if (location == null) location = spawnLocationNull();
        if (direction != null && controller.canBuildRobot(RobotType.POLITICIAN, direction, influence)) {
            controller.buildRobot(RobotType.POLITICIAN, direction, influence);
            setFlagForSpawnedUnit(direction, CommunicationECSpawnFlag.ACTION.ATTACK_LOCATION, CommunicationECSpawnFlag.SAFE_QUADRANT.NORTH_EAST, location);
            Debug.printInformation("SPAWNING ATTACKING POLI FOR LOCATION " + location + " FOR " + team,"VALID");
            if (team.equals(Cache.OPPONENT_TEAM)) {
                deployedPoliticianToAttackEnemy = true;
                foundECs.remove(location); //TODO: temporary solution for now
                --buildRequestsSZ;
                for (int i = 0; i < buildRequestsSZ; ++i) {
                    buildRequests[i] = buildRequests[i + 1];
                }
            }
            else if (team.equals(Team.NEUTRAL)) {
                deployedPoliticianToAttackNeutral = true;
                foundECs.remove(location);
                --buildRequestsSZ;
                for (int i = 0; i < buildRequestsSZ; ++i) {
                    buildRequests[i] = buildRequests[i + 1];
                }
            }

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
