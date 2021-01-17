package teambot.battlecode2021.util;
import battlecode.common.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Cache {
    public static RobotController controller;
    public static Team OUR_TEAM;
    public static Team OPPONENT_TEAM;
    public static RobotInfo[] ALL_NEARBY_ROBOTS;
    public static RobotInfo[] ALL_NEARBY_FRIENDLY_ROBOTS;
    public static RobotInfo[] ALL_NEARBY_ENEMY_ROBOTS;
    public static int[] ALL_NEARBY_FRIENDLY_ROBOT_FLAGS;
    public static MapLocation CURRENT_LOCATION;
    public static RobotType ROBOT_TYPE;
    public static int INFLUENCE;
    public static int CONVICTION;
    public static int ID;
    public static int SENSOR_RADIUS;

    public static MapLocation MAP_CENTER_LOCATION;

    // The size of the map
    //TODO: IMPLEMENT MAP DIMENSION UPDATES (based on partly with EC communication)
    public static int MAP_WIDTH;
    public static int MAP_HEIGHT;

    // The borders of the map
    //TODO: IMPLEMENT MAP CORNER UPDATES (based on partly with EC communication)
    public static int MAP_BOTTOM;
    public static int MAP_TOP;
    public static int MAP_LEFT;
    public static int MAP_RIGHT;
    public static int MAP_SYMMETRIC_TYPE;

    public static int[] EC_FLAGS;
    public static MapLocation myECLocation;
    public static int myECID;
    public static Map<MapLocation, Integer> ALL_KNOWN_FRIENDLY_EC_LOCATIONS; // Location : RobotID
    public static Map<MapLocation, Integer> ALL_KNOWN_ENEMY_EC_LOCATIONS; // Location : RobotID

    public static double PASSABILITY; // not sure about this...
    public static double COOLDOWN; // not sure...

    // for slanderer specifically important
    public static int NUM_ROUNDS_SINCE_SPAWN;

    //TODO: IMPLEMENT - REFACTOR SO IT'S JUST A METHOD TO SAVE A STATE AND WE USE CURR_POSITION - START_POSITION...
    // The moves we have performed from birth. So if we find something useful, we can set our flags accordingly with only taking 6 + 6 bits and communicate to the EC(s) the absolute location
    public static MapLocation START_LOCATION;
    public static Map<MapLocation, Integer> SAVE_DELTA_STATE; //TODO: KNOW WHAT EACH INTEGER REPRESENTS, OR

    //TODO: The EC needs to have additional parameters to hold all the IDs of the robots it produced
    public static Set<Integer> EC_ALL_PRODUCED_ROBOT_IDS; // IDs of the robots that were produced by the EC (class specific)

    //TODO: Save in 128x128 map the passability of grid (we need 128x128 since we do not know the offset of the map)


    // EC specific information
    public static int TOTAL_NUMBER_OF_ECS;

    public static void init(RobotController controller) throws GameActionException {
        Cache.controller = controller;
        OUR_TEAM = controller.getTeam();
        OPPONENT_TEAM = OUR_TEAM.opponent();
        ROBOT_TYPE = controller.getType();
        CURRENT_LOCATION = controller.getLocation();
        START_LOCATION = CURRENT_LOCATION;
        ID = controller.getID();
        NUM_ROUNDS_SINCE_SPAWN = 0;
        SENSOR_RADIUS = (int) Math.floor(Math.sqrt(Cache.ROBOT_TYPE.sensorRadiusSquared));
        EC_ALL_PRODUCED_ROBOT_IDS = new HashSet<>();
        ALL_KNOWN_FRIENDLY_EC_LOCATIONS = new HashMap<>();
        ALL_KNOWN_ENEMY_EC_LOCATIONS = new HashMap<>();

        myECLocation = Cache.CURRENT_LOCATION;

        if (ROBOT_TYPE != RobotType.ENLIGHTENMENT_CENTER) {
            for (RobotInfo robotInfo : controller.senseNearbyRobots(2, OUR_TEAM)) {
                if (robotInfo.type == RobotType.ENLIGHTENMENT_CENTER && controller.canGetFlag(robotInfo.ID)) {
                    int encoding = controller.getFlag(robotInfo.ID);
                    if (CommunicationECSpawnFlag.decodeIsSchemaType(encoding)) {
                        Direction directionFromEC = robotInfo.location.directionTo(START_LOCATION);
                        if (CommunicationECSpawnFlag.decodeDirection(encoding).equals(directionFromEC)) {
                            Debug.printInformation("FOUND MY EC AT ", robotInfo.location);
                            myECLocation = robotInfo.location;
                            myECID = robotInfo.ID;

                            processECSpawnInfo(encoding);

                            break;
                        }
                    }




                }
            }
        }
        Debug.printInformation("MY EC LOCATION IS ", myECLocation);

        //TODO: Not sure if I like using hashmap to store EC locations (is it bytecode expensive? Is there a different solution / can we create our own structure to hold ECs)?
        //TODO: Not sure how to determine / unadd if EC is captured/lost. I guess it's more reactive as we loop through...

    }

    //TODO:
    private static void processECSpawnInfo(int encoding) {
        CommunicationECSpawnFlag.ACTION actionInfo = CommunicationECSpawnFlag.decodeAction(encoding);
        CommunicationECSpawnFlag.SAFE_QUADRANT safeQuadrant = CommunicationECSpawnFlag.decodeSafeQuadrant(encoding);
        MapLocation locationData = CommunicationECSpawnFlag.decodeLocationData(encoding);

        // DO SOMETHING HERE
    }

    public static void loop() throws GameActionException {

        ++NUM_ROUNDS_SINCE_SPAWN;
        ALL_NEARBY_ROBOTS = controller.senseNearbyRobots();
        ALL_NEARBY_FRIENDLY_ROBOTS = controller.senseNearbyRobots(-1, OUR_TEAM);
        ALL_NEARBY_ENEMY_ROBOTS = controller.senseNearbyRobots(-1, OPPONENT_TEAM);
        CURRENT_LOCATION = controller.getLocation();
        INFLUENCE = controller.getInfluence();
        CONVICTION = controller.getConviction();
        PASSABILITY = controller.sensePassability(CURRENT_LOCATION);
        COOLDOWN = controller.getCooldownTurns();

//        if (ROBOT_TYPE != RobotType.ENLIGHTENMENT_CENTER) {
//            for (RobotInfo robot : ALL_NEARBY_ENEMY_ROBOTS) {
//                if (robot.getType() == RobotType.ENLIGHTENMENT_CENTER && robot.team == OPPONENT_TEAM) {
//                    MapLocation EC_MAP = robot.getLocation();
//                    int flag = Communication.makeFlag_SpecificedLocation(EC_MAP, "ENEMY_EC");
//                    if (controller.canSetFlag(flag)) {
//                        controller.setFlag(flag);
//                        break;
//                    }
//                }
//            }
//        }

    }
}
