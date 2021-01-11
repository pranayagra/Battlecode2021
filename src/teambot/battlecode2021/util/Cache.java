package teambot.battlecode2021.util;
import battlecode.common.*;

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
    public static Set<Integer> EC_ALL_PRODUCED_ROBOT_IDS; // IDs of the roberts that were produced by the EC (class specific)

    //TODO: Save in 128x128 map the passability of grid (we need 128x128 since we do not know the offset of the map)


    // EC specific information
    public static int TOTAL_NUMBER_OF_ECS;

    public static void init(RobotController controller) {
        Cache.controller = controller;
        OUR_TEAM = controller.getTeam();
        OPPONENT_TEAM = OUR_TEAM.opponent();
        ROBOT_TYPE = controller.getType();
        CURRENT_LOCATION = controller.getLocation();
        START_LOCATION = CURRENT_LOCATION;
        ID = controller.getID();
        NUM_ROUNDS_SINCE_SPAWN = 0;
        EC_ALL_PRODUCED_ROBOT_IDS = new HashSet<>();

        //TODO: Not sure if I like using hashmap to store EC locations (is it bytecode expensive? Is there a different solution / can we create our own structure to hold ECs)?
        //TODO: Not sure how to determine / unadd if EC is captured/lost. I guess it's more reactive as we loop through...

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

        for (RobotInfo robot : ALL_NEARBY_ROBOTS) {
            if (robot.getType() == RobotType.ENLIGHTENMENT_CENTER) {
                if (robot.team == OUR_TEAM) {
                    ALL_KNOWN_ENEMY_EC_LOCATIONS.remove(robot.location);
                    ALL_KNOWN_FRIENDLY_EC_LOCATIONS.put(robot.location, robot.ID);
                } else if (robot.team == OPPONENT_TEAM) {
                    ALL_KNOWN_ENEMY_EC_LOCATIONS.put(robot.location, robot.ID);
                    ALL_KNOWN_FRIENDLY_EC_LOCATIONS.remove(robot.location);
                }
            }
        }

        if (ROBOT_TYPE == RobotType.ENLIGHTENMENT_CENTER) {
            if (MAP_WIDTH == 0 || MAP_HEIGHT == 0 || MAP_BOTTOM == 0 || MAP_TOP == 0 || MAP_LEFT == 0 || MAP_RIGHT == 0) {
                for (Integer robotID : EC_ALL_PRODUCED_ROBOT_IDS) {
                    // TODO: CHECK FOR specific robotID flag if MAP information is missing from EC
                }
                if (MAP_WIDTH != 0 && (MAP_LEFT ^ MAP_RIGHT) != 0) {
                    if (MAP_LEFT == 0) MAP_LEFT = MAP_RIGHT - MAP_WIDTH;
                    if (MAP_RIGHT == 0) MAP_RIGHT = MAP_LEFT + MAP_WIDTH;
                }
                if (MAP_HEIGHT != 0 && (MAP_HEIGHT ^ MAP_BOTTOM) != 0) {
                    if (MAP_HEIGHT == 0) MAP_HEIGHT = MAP_BOTTOM + MAP_HEIGHT;
                    if (MAP_BOTTOM == 0) MAP_BOTTOM = MAP_HEIGHT - MAP_HEIGHT;
                }
            }
        }

    }
}
