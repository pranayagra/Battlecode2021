package teambot.battlecode2021.util;
import battlecode.common.*;
import teambot.battlecode2021.util.*;

public class Scout {

    public static RobotController controller;

    public static void init(RobotController controller) {
        Scout.controller = controller;
    }

    // Check edge of sense area for map edges
    public static void scoutMapEdges() throws GameActionException {
        MapLocation currentLocation = controller.getLocation();
        if (Cache.MAP_TOP == 0) {
            checkEdge(Direction.NORTH, currentLocation, Cache.SENSOR_RADIUS);
        }
        if (Cache.MAP_RIGHT == 0) {
            checkEdge(Direction.EAST,currentLocation, Cache.SENSOR_RADIUS);
        }
        if (Cache.MAP_LEFT == 0) {
            checkEdge(Direction.WEST,currentLocation, Cache.SENSOR_RADIUS);
        }
        if (Cache.MAP_BOTTOM == 0) {
            checkEdge(Direction.SOUTH,currentLocation, Cache.SENSOR_RADIUS);
        }  
        
    }

    // Checks edge. If not on map, set cache and send flag
    // Changed to one function instead of four
    private static void checkEdge(Direction dir, MapLocation center, int radius) throws GameActionException {

        // added switch so we do not communicate the same message and backlog queue
        switch(dir) {
            case NORTH:
                if (Cache.MAP_TOP != 0) return;
                break;
            case SOUTH:
                if (Cache.MAP_BOTTOM != 0) return;
                break;
            case EAST:
                if (Cache.MAP_RIGHT != 0) return;
                break;
            case WEST:
                if (Cache.MAP_LEFT != 0) return;
                break;
        }

        int x = dir.getDeltaX();
        int y = dir.getDeltaY();
        if (controller.onTheMap(center.translate(x*radius, y*radius))) return; //the furthest distance is already on the map (so the remaining must also be)
        int flag;
        for (int i = radius - 1; i != 0; --i) {
            MapLocation scanLocation = center.translate(x*i, y*i);
            if (controller.onTheMap(scanLocation)) {
                if (dir == Direction.NORTH) {
                    Cache.MAP_TOP=scanLocation.y;
                    flag = CommunicationLocation.encodeLOCATION(false, true, CommunicationLocation.FLAG_LOCATION_TYPES.NORTH_MAP_LOCATION, scanLocation);
                }
                else if (dir == Direction.SOUTH) {
                    Cache.MAP_BOTTOM=scanLocation.y;
                    flag = CommunicationLocation.encodeLOCATION(false, true, CommunicationLocation.FLAG_LOCATION_TYPES.SOUTH_MAP_LOCATION, scanLocation);
                }
                else if (dir == Direction.EAST) {
                    Cache.MAP_RIGHT=scanLocation.x;
                    flag = CommunicationLocation.encodeLOCATION(false, true, CommunicationLocation.FLAG_LOCATION_TYPES.EAST_MAP_LOCATION, scanLocation);
                }
                else {
                    Cache.MAP_LEFT=scanLocation.x;
                    flag = CommunicationLocation.encodeLOCATION(false, true, CommunicationLocation.FLAG_LOCATION_TYPES.WEST_MAP_LOCATION, scanLocation);
                }
                Debug.printInformation("Found edge", dir);
                Comms.checkAndAddFlag(flag);
                return;
            }
        }
    }

    // Check for ECs

    /* Find all ECs in sensor range and communicate it if and only if
    *  1) the bot has never seen it before OR
    *  2) the bot previously scouted it but has since changed teams (ally, enemy, neutral)
    * */

    //TODO: Not sure if hashmap best data structure. Bytecode inefficient - change to array?
    private void scoutECs() throws GameActionException {
        MapLocation currentLocation = controller.getLocation();
        RobotInfo[] nearbyRobots = controller.senseNearbyRobots();
        for (RobotInfo info : nearbyRobots) { 
            if (info.type == RobotType.ENLIGHTENMENT_CENTER) {
                Debug.printInformation( "checking EC location " + info.location + " => ", "");
                CommunicationLocation.FLAG_LOCATION_TYPES locationTypePrevious = Cache.FOUND_ECS.get(info.location);
                CommunicationLocation.FLAG_LOCATION_TYPES locationTypeNew = getECType(info.team);
                if (locationTypePrevious == null || locationTypePrevious != locationTypeNew) { //if null or if the type of EC has since changed
                    Cache.FOUND_ECS.put(info.location, locationTypeNew); //overwrite or add
                    int flag = CommunicationLocation.encodeLOCATION(false, false, locationTypeNew, info.location);
                    Comms.checkAndAddFlag(flag);
                    flag = CommunicationECInfo.encodeECInfo(false, false, getCommunicatedUnitTeamForECInfo(info.team), info.conviction);
                    Comms.checkAndAddFlag(flag);
                    flag = CommunicationRobotID.encodeRobotID(false,true, CommunicationRobotID.COMMUNICATION_UNIT_TYPE.EC, getCommunicatedUnitTeamForRobotID(info.team), info.ID);
                    Comms.checkAndAddFlag(flag);
                }
            }
        }
    }
    
    /* Converts the EC Team to a Location Flag Type for communication purposes */
    private CommunicationLocation.FLAG_LOCATION_TYPES getECType(Team ECTeam) {
        if (ECTeam.equals(Cache.OUR_TEAM)) {
            return CommunicationLocation.FLAG_LOCATION_TYPES.MY_EC_LOCATION;
        } else if (ECTeam.equals(Cache.OPPONENT_TEAM)) {
            return CommunicationLocation.FLAG_LOCATION_TYPES.ENEMY_EC_LOCATION;
        } else {
            return CommunicationLocation.FLAG_LOCATION_TYPES.NEUTRAL_EC_LOCATION;
        }
    }

    private CommunicationECInfo.COMMUNICATION_UNIT_TEAM getCommunicatedUnitTeamForECInfo(Team ECTeam) {
        if (ECTeam.equals(Cache.OUR_TEAM)) {
            return CommunicationECInfo.COMMUNICATION_UNIT_TEAM.MY;
        } else if (ECTeam.equals(Cache.OPPONENT_TEAM)) {
            return CommunicationECInfo.COMMUNICATION_UNIT_TEAM.ENEMY;
        } else {
            return CommunicationECInfo.COMMUNICATION_UNIT_TEAM.NEUTRAL;
        }
    }

    private CommunicationRobotID.COMMUNICATION_UNIT_TEAM getCommunicatedUnitTeamForRobotID(Team ECTeam) {
        if (ECTeam.equals(Cache.OUR_TEAM)) {
            return CommunicationRobotID.COMMUNICATION_UNIT_TEAM.MY;
        } else if (ECTeam.equals(Cache.OPPONENT_TEAM)) {
            return CommunicationRobotID.COMMUNICATION_UNIT_TEAM.ENEMY;
        } else {
            return CommunicationRobotID.COMMUNICATION_UNIT_TEAM.NEUTRAL;
        }
    }
}
