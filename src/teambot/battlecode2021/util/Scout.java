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
    private static void checkEdge(Direction dir, MapLocation center, int radius) throws GameActionException {

        int x = 0;
        int y = 0;
        if (dir == Direction.NORTH) {
            y = 1;
        }
        else if (dir == Direction.SOUTH) {
            y = -1;
        }
        else if (dir == Direction.EAST) {
            x = 1;
        }
        else {
            x = -1;
        }
        if (controller.onTheMap(center.translate(x*radius, y*radius))) return; //the furthest distance is already on the map (so the remaining must also be)
        int flag;
        for (int i = radius - 1; i != 0; --i) {
            MapLocation scanLocation = center.translate(x*i, y*i);
            if (controller.onTheMap(scanLocation)) {
                if (dir == Direction.NORTH) {
                    Cache.MAP_TOP=scanLocation.y;
                    flag = Communication.encode_ExtraANDLocationType_and_ExtraANDLocationData(
                        Constants.FLAG_EXTRA_TYPES.VERIFICATION_ENSURANCE, Constants.FLAG_LOCATION_TYPES.TOP_OR_BOTTOM_MAP_LOCATION, 1, scanLocation);
                }
                else if (dir == Direction.SOUTH) {
                    Cache.MAP_BOTTOM=scanLocation.y;
                    flag = Communication.encode_ExtraANDLocationType_and_ExtraANDLocationData(
                        Constants.FLAG_EXTRA_TYPES.VERIFICATION_ENSURANCE, Constants.FLAG_LOCATION_TYPES.TOP_OR_BOTTOM_MAP_LOCATION, 3, scanLocation);
                }
                else if (dir == Direction.EAST) {
                    Cache.MAP_RIGHT=scanLocation.x;
                    flag = Communication.encode_ExtraANDLocationType_and_ExtraANDLocationData(
                        Constants.FLAG_EXTRA_TYPES.VERIFICATION_ENSURANCE, Constants.FLAG_LOCATION_TYPES.LEFT_OR_RIGHT_MAP_LOCATION, 2, scanLocation);
                }
                else {
                    Cache.MAP_LEFT=scanLocation.x;
                    flag = Communication.encode_ExtraANDLocationType_and_ExtraANDLocationData(
                        Constants.FLAG_EXTRA_TYPES.VERIFICATION_ENSURANCE, Constants.FLAG_LOCATION_TYPES.LEFT_OR_RIGHT_MAP_LOCATION, 4, scanLocation);
                }
                Debug.printInformation("Found edge",dir);
                Communication.checkAndAddFlag(flag);
                return;
            }
        }
    }
    
}
