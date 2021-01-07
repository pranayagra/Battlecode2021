package teambot.battlecode2021.util;

import battlecode.common.*;
import teambot.*;
import java.util.*;

public class Pathfinding {

    private static RobotController controller;

    public static void init(RobotController controller) {
        Pathfinding.controller = controller;
    }

    // Returns false if cannot move

    public Boolean move(MapLocation targetLoc) throws GameActionException {

        MapLocation currentLoc = controller.getLocation();
        // Is it ready
        if (!controller.isReady()) return false;
        // Is target out of the map
        if (!controller.onTheMap(currentLoc.add(currentLoc.directionTo(targetLoc)))) return false;
        // TODO: Some type of basic BFS which is within bytecode limit
        // Potential choices
        System.out.println(currentLoc.directionTo(targetLoc));
        MapLocation a = currentLoc.add(currentLoc.directionTo(targetLoc));
        MapLocation b = currentLoc.add(currentLoc.directionTo(targetLoc).rotateRight());
        MapLocation c = currentLoc.add(currentLoc.directionTo(targetLoc).rotateLeft());
        MapLocation[] choices = new MapLocation[3];
            //Bytecode efficient insertion sort
        if (controller.canSenseLocation(a)) {
            choices[0] = a;
        }
        if (controller.canSenseLocation(b)) {
            double costA = 1.0/controller.sensePassability(a);
            double costB = 2.0/controller.sensePassability(b);
            if (costB < costA) {
                choices[0] = b;
                choices[1] = a;
            } 
            else {
                choices[1] = b;
            }
            if (controller.canSenseLocation(c)) {
                double costC = 2.0/controller.sensePassability(c);
                if (costC < Math.min(costA, costB)) {
                    choices[2] = choices[1];
                    choices[1] = choices[0];
                    choices[0] = c;
                } 
                else if (costC < costA || costC < costB) {
                    choices[2] = choices[1];
                    choices[1] = c;
                }
                else {
                    choices[2] = c;
                }
            }
        }
        else if (controller.canSenseLocation(c)) {
            if (2.0/controller.sensePassability(c) < 1.0/controller.sensePassability(a)) {
                choices[0] = c;
                choices[1] = a;
            }
        }
        
        // Move
        for (int i = 0; i <= 2; i ++) {
            if (choices[i] == null) {
                return false;
            }
            if (naiveMove(choices[i])) {
                return true;
            }
        }
        return false;
    }
    // Naive movement | error checks
    
    public Boolean naiveMove(Direction dir) throws GameActionException {
        if (controller.canMove(dir)) {
            controller.move(dir);
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
