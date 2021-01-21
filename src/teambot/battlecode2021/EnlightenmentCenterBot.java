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

    private static int[] enemyDirectionCounts; //8 values indicating how dangerous a side of the map is (used in spawning politicians/scouting)
    private static int[] wallDirectionReward; // 8 values for how close the wall is from a certain direction (used in spawning slanderers in conjuction to enemyDirectionCounts)
    private static int[] numSlanderersWallDirectionSpawned;
    private static final int CEIL_MAX_REWARD = 40;
    private static boolean hasFoundEnemies;

    private static MapLocation[] SCOUT_LOCATIONS;
    private static int SCOUT_LOCATIONS_CURRENT;

    //TODO (1/20): below
    /* Politicians that are attacking -- mid prio (should only be at most 100) --
    Also these are only "free" politicians that find out the EC they are trying to capture is already captured by our team */

    /* Politicians that are defending slanderers -- size for initial rounds */
    private static int POLITICIAN_DEFENDING_SLANDERER_SZ;

    /* Slanderer, useful to communicate danger or production of muckrakers, & to check if the slanderer converted to a politician -- mid prio */
    private static FastQueueSlanderers SLANDERER_IDs;

    /* Do we have one guide broadcasting information to newly created units */
    private static int GUIDE_ID = 0;

    /* Should always iterate on all of these -- highest priority (should be at most 100) */
    private static int SCOUT_MUCKRAKER_SZ;

    private static FastProcessIDs processRobots;
    private static boolean attackingLocationFlagSet;

    //Behavior: if does not exist, add to map. If exists, check type (neutral, friendly, enemy) and replace it
    private static Map <MapLocation, EC_Information> foundECs;

    private static MapLocation attackNeutralLocation;
    private static int attackNeutralLocationHealth;

    private static MapLocation attackEnemyLocation;
    private static int attackEnemyLocationHealth;

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

        /* NEW STUFF BELOW */

        enemyDirectionCounts = new int[8];
        wallDirectionReward = new int[8];
        numSlanderersWallDirectionSpawned = new int[8];

        /* PROCESS FLAGS THROUGH MULTIPLE ROUNDS */

        SLANDERER_IDs = new FastQueueSlanderers(152); //We cannot have more than 150ish slanderers

        SCOUT_MUCKRAKER_SZ = 0;
        POLITICIAN_DEFENDING_SLANDERER_SZ = 0;

        // Add to this array with negative health. Query through politicians and see if it still exists && it has no mission. If so, replace with positive health. Keep running sum
        //When we want to schedule an attack, we see if this sum + influence is enough (among all ECs too). Then ALL politicians are set to this location & health is replaced to negative
        // updateAttackPoliticians() --> first
        // readFlagsOfFriendlyECs() -->
        // determineIfAttackValid() -->
        //  Yes => replace all politician health by negative value
        //  No => do nothing

        processRobots = new FastProcessIDs(200, 3, controller);
        attackingLocationFlagSet = false;

        foundECs = new HashMap<>();

    }

    @Override
    public void turn() throws GameActionException {
        naiveBid();

        //TODO (1/20): sum up attacking politicans influence (and other ECs) and determine if EC should attack
        defaultTurn();

        Debug.printByteCode("EC END TURN BYTECODE => ");

    }

    /* LAZILY removes slanderer from the SLANDERER_IDs object 300 rounds after. It the ID is still valid, it is added to the attacking Politician list
    * CAUTION: This list is not up to date if a slanderer is dies within 300 rounds of spawn due to enemy attack, it is a lazy-type implementation to optimize speed
    *  */
    //TODO: test implementation and the condition value >= 300. Health update is reactively to slanderer flag
    public void slanderersToPoliticians() {
        if (!SLANDERER_IDs.isEmpty()) {
            if (controller.getRoundNum() - SLANDERER_IDs.getFrontCreationTime() >= 300) { //need to retire slanderer as 1) it got killed or 2) converted
                int robotID = SLANDERER_IDs.getFrontID();
                SLANDERER_IDs.removeFront();
                if (controller.canGetFlag(robotID)) {
                    processRobots.addItem(robotID, FastProcessIDs.TYPE.PASSIVE_ATTACKING_POLITICIAN, 0);
                }
            }
        }
        //Debug.printInformation("updateSlanderers() => ", " VALID ");
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
        CommunicationMovement.MOVEMENT_BOTS_DATA movementBotData = CommunicationMovement.decodeMyPreferredMovement(encoding);
        CommunicationMovement.COMMUNICATION_TO_OTHER_BOTS communicationToOtherBots = CommunicationMovement.decodeCommunicationToOtherBots(encoding);
        int amount = CommunicationMovement.decodeDangerDirections(encoding) + 1;
        int direction = CommunicationMovement.convert_MovementBotData_DirectionInt(movementBotData);
        if (communicationToOtherBots == CommunicationMovement.COMMUNICATION_TO_OTHER_BOTS.SPOTTED_ENEMY_UNIT) {
            // WE SPOTTED AN ENEMY AT Direction movementBotData (EC => enemy location)
            enemyDirectionCounts[direction] += amount;
            enemyDirectionCounts[(direction + 7) % 8] += amount/2;
            enemyDirectionCounts[(direction + 1) % 8] += amount/2;
            hasFoundEnemies = true;
        } else if (communicationToOtherBots == CommunicationMovement.COMMUNICATION_TO_OTHER_BOTS.SEND_DEFENDING_POLITICIANS) {
            //TODO (1/17 -- maybe not needed): send politician to defend at direction location
        }

        return true;
    }

    private boolean parseCommsRobotID(int encoding) {
        Debug.printInformation("USED UNIMPLEMENTED METHOD parseCommsRobotID() ", " ERROR");
        CommunicationRobotID.COMMUNICATION_UNIT_TYPE communicatedUnitType = CommunicationRobotID.decodeCommunicatedUnitType(encoding);
        CommunicationRobotID.COMMUNICATION_UNIT_TEAM communicatedUnitTeam = CommunicationRobotID.decodeCommunicatedUnitTeam(encoding);
        int robotID = CommunicationRobotID.decodeRobotID(encoding);
        switch (communicatedUnitType) {
            case EC:
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

    private boolean parseCommsECDataSmall(int encoding) {
        int health = CommunicationECDataSmall.decodeHealth(encoding);
        MapLocation location = CommunicationECDataSmall.decodeLocationData(encoding);
        boolean isNeutralTeam = CommunicationECDataSmall.decodeIsEnemyVSNeutralTeam(encoding);
        if (isNeutralTeam) {
            foundECs.put(location, new EC_Information(location, health, -1, controller.getRoundNum(), CommunicationLocation.FLAG_LOCATION_TYPES.NEUTRAL_EC_LOCATION));
        } else {
            foundECs.put(location, new EC_Information(location, health, -1, controller.getRoundNum(), CommunicationLocation.FLAG_LOCATION_TYPES.ENEMY_EC_LOCATION));
        }
        return true;
    }

    /* converts both politicans that no longer have an active place to attack and 300 round surviving slanderers to passive politicians */
    public boolean parseCommsHealth(int encoding, int attackPoliticianIDX) {
        int health = CommunicationHealth.decodeRobotHealth(encoding);
        switch (CommunicationHealth.decodeCommunicatedUnitTeam(encoding)) {
            case CONVERTING_TO_PASSIVE_POLITICIAN:
                processRobots.typeForRobotID[attackPoliticianIDX] = FastProcessIDs.TYPE.PASSIVE_ATTACKING_POLITICIAN;
                processRobots.healthForRobotID[attackPoliticianIDX] = health;
                break;
            case ENEMY_BUTNOTEC:
                break;
        }
        return true;
    }

    public void urgentFlagRecieved(int encoding, int attackingPoliticianIDX) throws GameActionException {
        if (CommunicationLocation.decodeIsSchemaType(encoding)) {
            parseCommsLocation(encoding);
        } else if (CommunicationMovement.decodeIsSchemaType(encoding)) {
            parseCommsMovement(encoding);
        } else if (CommunicationRobotID.decodeIsSchemaType(encoding)) {
            parseCommsRobotID(encoding);
        } else if (CommunicationHealth.decodeIsSchemaType(encoding)) {
            parseCommsHealth(encoding, attackingPoliticianIDX);
        }  else if (CommunicationECDataSmall.decodeIsSchemaType(encoding)) {
            parseCommsECDataSmall(encoding);
        }
    }

    public void processRobotFlag(int robotIDX, int messageSize) throws GameActionException {

        if (messageSize == 1) {
            // this is a single message => simple case (just use urgent routine)
            urgentFlagRecieved(processRobots.flagsForRobotID[robotIDX][0], robotIDX);
        } else if (messageSize == 2) {
            int flag1 = processRobots.flagsForRobotID[robotIDX][0];
            int flag2 = processRobots.flagsForRobotID[robotIDX][1];
            if (CommunicationLocation.decodeIsSchemaType(flag1) && CommunicationRobotID.decodeIsSchemaType(flag2)) {
                //FRIENDLY EC INFO -> location + ID
                MapLocation ECLocation = CommunicationLocation.decodeLocationData(flag1);
                CommunicationLocation.FLAG_LOCATION_TYPES ECTeam = CommunicationLocation.decodeLocationType(flag1);
                int ECRobotID = CommunicationRobotID.decodeRobotID(flag2);
                EC_Information ecInfo = new EC_Information(ECLocation, -1, ECRobotID, controller.getRoundNum(), ECTeam);
                foundECs.put(ECLocation, ecInfo);
                //Debug.printInformation("EC Received ECScoutInformation ", ecInfo.toString());
            }
        } else if (messageSize == 3) {
            // this is three messages => most likely Location + ECInfo + RobotID
            int flag1 = processRobots.flagsForRobotID[robotIDX][0];
            int flag2 = processRobots.flagsForRobotID[robotIDX][1];
            int flag3 = processRobots.flagsForRobotID[robotIDX][2];
            if (CommunicationLocation.decodeIsSchemaType(flag1) &&
                    CommunicationHealth.decodeIsSchemaType(flag2) &&
                    CommunicationRobotID.decodeIsSchemaType(flag3)) {
                MapLocation ECLocation = CommunicationLocation.decodeLocationData(flag1);
                CommunicationLocation.FLAG_LOCATION_TYPES ECTeam = CommunicationLocation.decodeLocationType(flag1);
                int ECHealth = CommunicationHealth.decodeRobotHealth(flag2);
                int ECRobotID = CommunicationRobotID.decodeRobotID(flag3);
                EC_Information ecInfo = new EC_Information(ECLocation, ECHealth, ECRobotID, controller.getRoundNum(), ECTeam);
                foundECs.put(ECLocation, ecInfo);
                //Debug.printInformation("EC Received ECScoutInformation ", ecInfo.toString());
            }
            //NOTE -> REMEMBER THAT IF WE HAVE OTHER TYPES OF MULTI-ROUND FLAGS, WE WILL NEED TO ADD CONDITIONS HERE... CURRENTLY "HARDCODED"
        }

        // The robot is still of value (do not remove from system), but we must reset the size of the current message
        processRobots.numFlagsForRobotID[robotIDX] = 0;

    }

    /* Iterative over all friendly scout flags and parses the flag for the information. Assumes the list size will not go over 152 elements (risky)
    *
    *
    *  */
    public void iterateAllUnitIDs() throws GameActionException {
        processRobots.resetIterator();

        for (int i = 0; i < processRobots.currentSize() + 50; ++i) {
            if (!processRobots.nextIdxExists()) break;
            /*
                -1 => return
                 0 => continue
                 1 => isUrgent or communicationECDataSmall
                 2 => decodeIsLastFlag
                 3 => added to flag
             */

            if (processRobots.typeForRobotID[i] == FastProcessIDs.TYPE.PASSIVE_ATTACKING_POLITICIAN && attackingLocationFlagSet) {
                processRobots.typeForRobotID[i] = FastProcessIDs.TYPE.ACTIVE_ATTACKING_POLITICIAN;
            }

            int encodedFlag = controller.getFlag(processRobots.robotIDs[i]);
            if (encodedFlag == 0) {
                processRobots.updatePassivePoliticianAttackDamage(i);
                continue;
            }

            if (Comms.decodeIsUrgent(encodedFlag) || CommunicationECDataSmall.decodeIsSchemaType(encodedFlag)) {
                urgentFlagRecieved(encodedFlag, i);
            } else if (Comms.decodeIsLastFlag(encodedFlag)) {
                processRobots.flagsForRobotID[i][processRobots.numFlagsForRobotID[i]++] = encodedFlag;
                processRobotFlag(i, processRobots.numFlagsForRobotID[i]);
            } else {
                processRobots.flagsForRobotID[i][processRobots.numFlagsForRobotID[i]++] = encodedFlag;
            }

            processRobots.updatePassivePoliticianAttackDamage(i);
        }

    }

    private void processAllECInformation() {

        attackEnemyLocation = null;
        attackEnemyLocationHealth = 9999999;

        attackNeutralLocation = null;
        attackNeutralLocationHealth = 9999999;

        for (Map.Entry<MapLocation, EC_Information> entry : foundECs.entrySet()) {

            MapLocation location = entry.getKey();
            EC_Information ECInfo = entry.getValue();

            if (ECInfo.health < attackNeutralLocationHealth && ECInfo.team == Team.NEUTRAL) {
                attackNeutralLocationHealth = ECInfo.health;
                attackNeutralLocation = location;
            }

            if (ECInfo.health < attackEnemyLocationHealth && ECInfo.team == Cache.OPPONENT_TEAM) {
                attackEnemyLocationHealth = ECInfo.health;
                attackEnemyLocation = location;
            }
        }
    }

    public void defaultTurn() throws GameActionException {
        slanderersToPoliticians();
        iterateAllUnitIDs();
        processAllECInformation();
        updateWallDistance();

        // IF NO INFLUENCE, SPAWN MUCKRAKER
        if (controller.getInfluence() <= 15) {
            spawnScoutMuckraker(1, randomValidDirection(), null);
            return;
        }

        //ATTACK NEUTRAL EC
        attackingLocationFlagSet = false;
        int totalCurrentDamageOnMap = processRobots.getPassivePoliticianAttackDamage();
        //TODO (1/21): calculate total damage capable of other ECs too

        //TODO (1/21): think about the health we want when capturing vs leaving our base and also if attackingLocationFlagSet should technically be in spawnAttackingPoli instead
        if (attackNeutralLocationHealth != 9999999 && attackNeutralLocationHealth / 2 <= (controller.getInfluence() + totalCurrentDamageOnMap) * controller.getEmpowerFactor(Cache.OUR_TEAM, 15)) {
            // I want to do half health dmg
            int influenceToSpend = Math.max(0, (attackNeutralLocationHealth / 2) - totalCurrentDamageOnMap + 0); // we want to have 0 health at minimum of new captured EC
            influenceToSpend = (int) (Math.max(influenceToSpend, (controller.getInfluence() - 30) * 0.5));
            if (controller.getInfluence() - 30 >= influenceToSpend) { //we want to have at minimum 30 health left in our base
                attackingLocationFlagSet = true;
                Debug.printInformation("ATTACKING NEUTRAL AT " + attackNeutralLocation + " WITH MAP DMG " + totalCurrentDamageOnMap, attackNeutralLocationHealth);
                spawnAttackingPolitician(influenceToSpend, toBuildDirection(Cache.START_LOCATION.directionTo(attackNeutralLocation), 4), attackNeutralLocation, Team.NEUTRAL);
                return;
            }
        }

        if (attackEnemyLocationHealth != 9999999 && attackEnemyLocationHealth / 2 <= (controller.getInfluence() + totalCurrentDamageOnMap) * controller.getEmpowerFactor(Cache.OUR_TEAM, 15)) {
            int influenceToSpend = Math.max(0, (attackEnemyLocationHealth / 3) - totalCurrentDamageOnMap + 0);
            influenceToSpend = (int) (Math.max(influenceToSpend, (controller.getInfluence() - 30) * 0.5));
            if (controller.getInfluence() - 30 >= influenceToSpend) { //we want to have at minimum 30 health left in our base
                attackingLocationFlagSet = true;
                Debug.printInformation("ATTACKING ENEMY WITH MAP DMG " + totalCurrentDamageOnMap , " VALID");
                spawnAttackingPolitician(influenceToSpend, toBuildDirection(Cache.START_LOCATION.directionTo(attackEnemyLocation), 4), attackEnemyLocation, Cache.OPPONENT_TEAM);
                return;
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
            int influenceSpend = 15;
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
                //Debug.printInformation("Spawned Scout => ", targetLocation);
            }
        } else if (randomInt == 10 && POLITICIAN_DEFENDING_SLANDERER_SZ <= 10) {
            Direction dir = randomValidDirection();
            if (dangerDirection != null) dir = dangerDirection;
            int influenceSpend = 15;
            spawnDefendingPolitician(influenceSpend, toBuildDirection(dir,3),null);
        }

        randomInt = random.nextInt(10) + 1;

        if (randomInt <= 5) {
            spawnScoutMuckraker(1, randomValidDirection(), null);
        } else if (randomInt <= 7 && SLANDERER_IDs.getSize() >= 3) {
            Direction dir = randomValidDirection();
            if (dangerDirection != null) dir = dangerDirection;
            int influenceSpend = 15;
            spawnDefendingPolitician(influenceSpend, toBuildDirection(dir, 3), null);
        } else {
            spawnLatticeSlanderer((int) (controller.getInfluence() * 0.65), safeDirection);
        }

    }

    /* Simple bidding strategy */ 

    private void naiveBid() throws GameActionException {
        int bid = Math.max(1, controller.getInfluence() / 100);
        if (controller.canBid(bid)){
            controller.bid(bid);
        }
    }

    private boolean setFlagForSpawnedUnit(Direction direction, CommunicationECSpawnFlag.ACTION actionType, CommunicationECSpawnFlag.SAFE_QUADRANT safeQuadrant, MapLocation locationData) throws GameActionException {
        int flag = CommunicationECSpawnFlag.encodeSpawnInfo(direction, actionType, safeQuadrant, locationData);
        if (!Comms.canScheduleFlag(controller.getRoundNum() + 1)) {
            Debug.printInformation("Warning - Potential Schedule Conflict at turn", controller.getRoundNum()+1);
            return false;
        }
        if (Comms.scheduleFlag(controller.getRoundNum() + 1, flag)) {
            return true;
        }
        return false;
    }

    private boolean spawnScoutMuckraker(int influence, Direction direction, MapLocation location) throws GameActionException {
        //TODO: should spawn muckraker in location
        if (location == null) location = spawnLocationNull();

        // Only build if message can be sent out
        if (direction != null && controller.canBuildRobot(RobotType.MUCKRAKER, direction, influence)) {
            if (setFlagForSpawnedUnit(direction, CommunicationECSpawnFlag.ACTION.SCOUT_LOCATION, CommunicationECSpawnFlag.SAFE_QUADRANT.NORTH_EAST, location)) {
                controller.buildRobot(RobotType.MUCKRAKER, direction, influence);
                SCOUT_MUCKRAKER_SZ++;
                int robotID = controller.senseRobotAtLocation(Cache.CURRENT_LOCATION.add(direction)).ID;
                processRobots.addItem(robotID, FastProcessIDs.TYPE.SCOUT, influence);
                return true;
            }
        }
        return false;

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
            processRobots.addItem(GUIDE_ID, FastProcessIDs.TYPE.GUIDE_SCOUT, influence);
        }
    }

    private void spawnLatticeSlanderer(int influence, Direction direction) throws GameActionException {
        //TODO: should spawn slanderer, which default behavior is to build lattice
        if (direction != null && controller.canBuildRobot(RobotType.SLANDERER, direction, influence)) {
            controller.buildRobot(RobotType.SLANDERER, direction, influence);
            numSlanderersWallDirectionSpawned[direction.ordinal()]++;
            SLANDERER_IDs.push(controller.senseRobotAtLocation(Cache.CURRENT_LOCATION.add(direction)).ID, controller.getRoundNum());
        }
    }

    private void spawnDefendingPolitician(int influence, Direction direction, MapLocation location) throws GameActionException {
        //TODO: should defend slanderers outside the muckrakers wall
        if (location == null) location = spawnLocationNull();
        if (direction != null && controller.canBuildRobot(RobotType.POLITICIAN, direction, influence)) {
            controller.buildRobot(RobotType.POLITICIAN, direction, influence);
            /* DO NOT KEEP TRACK OF DEFENDING POLITICIANS FOR NOW */
            setFlagForSpawnedUnit(direction, CommunicationECSpawnFlag.ACTION.DEFEND_LOCATION, CommunicationECSpawnFlag.SAFE_QUADRANT.NORTH_EAST, location);
        }
    }

    private void spawnAttackingPolitician(int influence, Direction direction, MapLocation location, Team team) throws GameActionException {
        //TODO: should only be called if the politician is meant to attack some base -> need to create politician with enough influence, set my EC flag to the location + attacking poli
        //Assumption: politician upon creation should read EC flag and know it's purpose in life. It can determine what to do then
        Debug.printInformation("TRYING TO CREATE " + influence + " POLI AT " + direction + " ATTACKING LOCATION " + location + " ??", " TRYME");
        if (location == null) location = spawnLocationNull();
        if (direction != null && controller.canBuildRobot(RobotType.POLITICIAN, direction, influence)) {
            Debug.printInformation("CREATED " + influence + " POLI AT " + direction + " ATTACKING LOCATION " + location, " VALID");
            controller.buildRobot(RobotType.POLITICIAN, direction, influence);
            setFlagForSpawnedUnit(direction, CommunicationECSpawnFlag.ACTION.ATTACK_LOCATION, CommunicationECSpawnFlag.SAFE_QUADRANT.NORTH_EAST, location);
            int robotID = controller.senseRobotAtLocation(Cache.CURRENT_LOCATION.add(direction)).ID;
            processRobots.addItem(robotID, FastProcessIDs.TYPE.ACTIVE_ATTACKING_POLITICIAN, influence);
        }
    }

    //Determine how close each wall is to the EC to guide slanderer spawn location
    private void updateWallDistance() {
        //for all 8 directions calculate distance
        //for direction, compute distance. for midway, compute average. use as heurstic!
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

    /* Used to determine which direction a slanderer should be spawned in */
    private Direction checkSpawnSlanderers() {
        int bestWallDir = -1;
        int bestReward = -999999;

        boolean noWallsFound = false;
        if (Cache.MAP_TOP == 0 && Cache.MAP_RIGHT == 0 && Cache.MAP_BOTTOM == 0 && Cache.MAP_LEFT == 0) noWallsFound = true;

        if (noWallsFound && hasFoundEnemies) {
            //spawn away from enemies
            for (int i = 0; i < 8; ++i) {
                int totalReward = -enemyDirectionCounts[i];
                if (bestReward < totalReward && enemyDirectionCounts[i] <= Cache.NUM_ROUNDS_SINCE_SPAWN / 2) {
                    bestReward = totalReward;
                    bestWallDir = i;
                }
            }
        } else if (!noWallsFound) { //found walls
            for (int i = 0; i < 8; ++i) {
                int totalReward = wallDirectionReward[i] - numSlanderersWallDirectionSpawned[i] - enemyDirectionCounts[i];
                if (bestReward < totalReward && wallDirectionReward[i] >= 5 && enemyDirectionCounts[i] <= Cache.NUM_ROUNDS_SINCE_SPAWN / 2) {
                    bestReward = totalReward;
                    bestWallDir = i;
                }
            }
        }

        for (RobotInfo robotInfo : Cache.ALL_NEARBY_ENEMY_ROBOTS) {
            if (robotInfo.type == RobotType.MUCKRAKER || robotInfo.type == RobotType.ENLIGHTENMENT_CENTER) return null;
        }

        if (bestWallDir != -1) {
            Direction toBuild = toBuildDirection(Direction.values()[bestWallDir], 3);
            return toBuild;
        }

        return null;
    }

    /* Used to determine which direction a defensive politician is spawned in */
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

    private MapLocation spawnLocationNull() {
//        return Cache.CURRENT_LOCATION;
        return Pathfinding.randomLocation();
    }

}
