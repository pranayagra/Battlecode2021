package teambot.battlecode2021.util;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;

public class Util {
    private static RobotController controller;

    public static void init(RobotController controller) {
        Util.controller = controller;
    }

    public static void loop() throws GameActionException {
        Cache.loop();
        Comms.hasSetFlag = false;
    }

    public static void postLoop() throws GameActionException {
        // Scouting
        Scout.scoutMapEdges();
        Comms.loop();
    }

    private int addedLocationDistance(MapLocation one, MapLocation two) {
        return Math.abs(one.x - two.x) + Math.abs(one.y - two.y);
    }

    public boolean moveAwayFromLocation(MapLocation currentLocation, MapLocation avoidLocation) throws GameActionException {

        if (!controller.isReady()) return false;
        
        int maximizedDistance = addedLocationDistance(currentLocation, avoidLocation);
        Direction maximizedDirection = null;

        for (Direction direction : Constants.DIRECTIONS) {
            if (controller.canMove(direction)) {
                MapLocation candidateLocation = currentLocation.add(direction);
                int candidateDistance = addedLocationDistance(currentLocation, candidateLocation);
                if (candidateDistance > maximizedDistance) {
                    maximizedDistance = candidateDistance;
                    maximizedDirection = direction;
                }
            }
        }

        if (maximizedDirection != null) {
            controller.move(maximizedDirection);
            return true;
        }

        return false;
    }
}
