package finalbot11.battlecode2021.util;
import battlecode.common.*;

import java.util.HashMap;
import java.util.Map;

public class Cache {
    public static RobotController controller;
    public static Team OUR_TEAM;
    public static Team OPPONENT_TEAM;
    public static RobotInfo[] ALL_NEARBY_ROBOTS;
    public static RobotInfo[] ALL_NEARBY_FRIENDLY_ROBOTS;
    public static RobotInfo[] ALL_NEARBY_ENEMY_ROBOTS;
    public static MapLocation CURRENT_LOCATION;
    public static RobotType ROBOT_TYPE;
    public static int INFLUENCE;
    public static int CONVICTION;
    public static int ID;
    public static int SENSOR_RADIUS;

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

    public static MapLocation myECLocation;
    public static int myECID;

    public static Map<MapLocation, CommunicationLocation.FLAG_LOCATION_TYPES> FOUND_ECS;

    public static int NUM_ROUNDS_SINCE_SPAWN;

    public static MapLocation START_LOCATION;

    public static CommunicationECSpawnFlag.ACTION EC_INFO_ACTION;
    public static MapLocation EC_INFO_LOCATION;
    public static Direction EC_INFO_DIRECTION;

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
        FOUND_ECS = new HashMap();

        // Find EC spawn
        myECLocation = Cache.CURRENT_LOCATION;
        if (ROBOT_TYPE != RobotType.ENLIGHTENMENT_CENTER) {
            for (RobotInfo robotInfo : controller.senseNearbyRobots(2, OUR_TEAM)) {
                if (robotInfo.type == RobotType.ENLIGHTENMENT_CENTER && controller.canGetFlag(robotInfo.ID)) {
                    int encoding = controller.getFlag(robotInfo.ID);
                    myECLocation = robotInfo.location;
                    myECID = robotInfo.ID;
                    if (CommunicationECSpawnFlag.decodeIsSchemaType(encoding)) {
                        Direction directionFromEC = robotInfo.location.directionTo(START_LOCATION);
                        if (CommunicationECSpawnFlag.decodeDirection(encoding).equals(directionFromEC)) {
                            processECSpawnInfo(encoding);
                            break;
                        }
                    }
                }
            }
        }
        FOUND_ECS.put(myECLocation, CommunicationLocation.FLAG_LOCATION_TYPES.MY_EC_LOCATION);

        //TODO: Not sure if I like using hashmap to store EC locations (is it bytecode expensive? Is there a different solution / can we create our own structure to hold ECs)?
        //TODO: Not sure how to determine / unadd if EC is captured/lost. I guess it's more reactive as we loop through...

    }

    //TODO: change depending on flag.
    private static void processECSpawnInfo(int encoding) {
        EC_INFO_ACTION = CommunicationECSpawnFlag.decodeAction(encoding);
        EC_INFO_LOCATION = CommunicationECSpawnFlag.decodeLocationData(encoding);
        EC_INFO_DIRECTION = CommunicationECSpawnFlag.decodeDirection(encoding);
    }

    public static void loop() {
        ++NUM_ROUNDS_SINCE_SPAWN;
        ALL_NEARBY_ROBOTS = controller.senseNearbyRobots();
        ALL_NEARBY_FRIENDLY_ROBOTS = controller.senseNearbyRobots(-1, OUR_TEAM);
        ALL_NEARBY_ENEMY_ROBOTS = controller.senseNearbyRobots(-1, OPPONENT_TEAM);
        CURRENT_LOCATION = controller.getLocation();
        INFLUENCE = controller.getInfluence();
        CONVICTION = controller.getConviction();

        if (MAP_TOP != 0 && MAP_TOP < CURRENT_LOCATION.y) {
            MAP_TOP = MAP_TOP + 128;
        }
        if (MAP_BOTTOM != 0 && MAP_BOTTOM > CURRENT_LOCATION.y) {
            MAP_BOTTOM = MAP_BOTTOM - 128;
        }
        if (MAP_RIGHT != 0 && MAP_RIGHT < CURRENT_LOCATION.x) {
            MAP_RIGHT = MAP_RIGHT + 128;
        }
        if (MAP_LEFT != 0  && MAP_LEFT > CURRENT_LOCATION.x) {
            MAP_LEFT = MAP_LEFT - 128;
        }

        if (MAP_TOP != 0 && MAP_BOTTOM != 0) {
            MAP_HEIGHT = MAP_TOP - MAP_BOTTOM;
        }

        if (MAP_LEFT != 0 && MAP_RIGHT != 0) {
            MAP_WIDTH = MAP_RIGHT - MAP_LEFT;
        }
    }
}
