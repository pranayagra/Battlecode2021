package teambot.battlecode2021.util;

import battlecode.common.*;
import teambot.battlecode2021.PoliticanBot;

import java.util.*;

public class Pathfinding {

    private static RobotController controller;

    public static void init(RobotController controller) {
        Pathfinding.controller = controller;
    }

    // Returns false if cannot move
    //TODO: Finish, use naive move for now
    public Boolean move(MapLocation loc) throws GameActionException {
        controllerLoc = controller.location;
        // Is it ready
        if (!controller.isReady()) {
            return false;
        }
        // Is target out of the map
        if (controller.canSenseRadiusSquared(loc.distanceSquaredTo(controller)) && controller.onTheMap(loc)) {
            return false;
        }

        /*
        // Calculate policy to head toward goal location
        -- Bellman optimality equation, DP
        */

        //TODO: Convert to graph form
            // Store nodes
        ArrayList<MapLocations> sensedLocs = new ArrayList<MapLocation>();
        for (x=-5;x<=5;x++) {
            for (y=-5;y<=5;x++) {
                if (controller.canSenseLocation(controllerLoc.translate(x,y))) {
                    sensedLocs.add(controllerLoc.translate(x,y));
                }
            }
        }
        //TODO: Calculate rewards
        //TODO: Inductive backtracking
        return true;
        
        

    }

    // Naive movement | error checks
    
    public Boolean naiveMove(Direction dir) throws GameActionException {
        if (rc.canMove(dir)) {
            rc.move(dir);
            return true;
        }
        return false;
    }

    public Boolean naiveMove(MapLocation loc) throws GameActionException {
        return this.naiveMove(controller.getLocation().directionTo(loc));
    }

    // Util
    public static Integer travelDistance(MapLocation a, MapLocation b) {
        return Math.min(Math.abs(a.x-b.x), Math.abs(a.y-b.y));
    }

}
