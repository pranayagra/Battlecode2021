package teambot.battlecode2021.util;

import battlecode.common.*;
import teambot.*;
import java.util.*;

public class Pathfinding {

    private static RobotController controller;

    public static void init(RobotController controller) {
        Pathfinding.controller = controller;
    }

    // Multiple navigation algorithm
    static boolean isBugging = false;
    static boolean clockwise = true;
    static int count = 0;
    static MapLocation prevTarget;
    static MapLocation startBugLocation;
    static MapLocation obstacle;
    static double gradient;
    static int stuckTurns;

    public static int move(MapLocation targetLoc) throws GameActionException {
        //Debugging
        if (Debug.debug) {
            controller.setIndicatorLine(Cache.CURRENT_LOCATION, targetLoc, 255, 0, 0);
        }
        // Is it ready
        if (!controller.isReady()) return 0;
        // Is target out of the map
        if (!controller.onTheMap(Cache.CURRENT_LOCATION.add(Cache.CURRENT_LOCATION.directionTo(targetLoc)))) return 2;
        // At destination
        if (targetLoc.equals(Cache.CURRENT_LOCATION)) {
            isBugging = false;
            return 3;
        }
        // Target change, stop bugging
        if (targetLoc != prevTarget) {
            isBugging = false;
        }
        prevTarget = targetLoc;

        if (!isBugging) {
            int res = greedyMove(targetLoc);
            // Can move
            if (res == 1 || res == 2) {
                return res;
            }
            // Stuck, start bugging
            isBugging = true;
            clockwise = true;
            count = 0;
            startBugLocation = Cache.CURRENT_LOCATION;
            gradient = calculateGradient(Cache.CURRENT_LOCATION, targetLoc);
            obstacle = controller.adjacentLocation(Cache.CURRENT_LOCATION.directionTo(targetLoc));
            stuckTurns = 0;
            return move(targetLoc);
        }
        else {
            // Robot trapped
            if (startBugLocation.equals(Cache.CURRENT_LOCATION)) {
                count += 1;
                if (count >= 3) {
                    return 0;
                }
            }
            Direction obstacleDirection = Cache.CURRENT_LOCATION.directionTo(obstacle);
            Direction targetDirection = obstacleDirection;
            // Edge Case: Obstacle gone
            if (naiveMove(obstacleDirection)) {
                isBugging = false;
                return 1;
            }
            if (clockwise) {
                targetDirection = targetDirection.rotateRight();
            } else {
                targetDirection = targetDirection.rotateLeft();
            }
            while (!naiveMove(targetDirection)) {
                if (clockwise) {
                    targetDirection = targetDirection.rotateRight();
                } else {
                    targetDirection = targetDirection.rotateLeft();
                }
                //If on the edge of the map, switch bug directions
                //Or, there is no way past
                if (!controller.onTheMap(controller.adjacentLocation(targetDirection))) {
                    if (clockwise) {
                        clockwise = false;
                        targetDirection = targetDirection.rotateLeft();
                        return move(targetLoc);
                    } else {
                        stuckTurns += 1;
                        if (Debug.debug) {
                            System.out.println("I am stuck.");
                        }
                        return 0;
                    }
                }
                if (targetDirection == obstacleDirection) {
                    stuckTurns += 1;
                    return 0;
                }
            }
            if (clockwise) {
                obstacle = controller.adjacentLocation(targetDirection.rotateLeft());
            } else {
                obstacle = controller.adjacentLocation(targetDirection.rotateRight());
            }
            MapLocation moveLoc = controller.adjacentLocation(targetDirection);
            //Check if it's passing the original line closer to the target
            if (Cache.CURRENT_LOCATION.distanceSquaredTo(targetLoc) < startBugLocation.distanceSquaredTo(targetLoc)) {
                if (calculateGradient(Cache.CURRENT_LOCATION, targetLoc) > gradient && calculateGradient(moveLoc, targetLoc) <= gradient) {
                    isBugging = false;
                }
                else if (calculateGradient(moveLoc, targetLoc) >= gradient) {
                    isBugging = false;
                }
            }
            if (naiveMove(targetDirection)) {
                return 1;
            }
            return 0;
        }
    }

    // Geedily from 3 naive options
    public static int greedyMove(MapLocation targetLoc) throws GameActionException {

        // TODO: Some type of basic BFS which is within bytecode limit
        // Potential choices
        MapLocation a = Cache.CURRENT_LOCATION.add(Cache.CURRENT_LOCATION.directionTo(targetLoc));
        MapLocation b = Cache.CURRENT_LOCATION.add(Cache.CURRENT_LOCATION.directionTo(targetLoc).rotateRight());
        MapLocation c = Cache.CURRENT_LOCATION.add(Cache.CURRENT_LOCATION.directionTo(targetLoc).rotateLeft());
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
                return 0;
            }
            if (naiveMove(choices[i])) {
                return 1;
            }
        }
        return 0;
    }
    // Naive movement | error checks

    public static Boolean naiveMove(Direction dir) throws GameActionException {
        if (controller.canMove(dir)) {
            controller.move(dir);
            return true;
        }
        return false;
    }

    public static Boolean naiveMove(MapLocation loc) throws GameActionException {
        return naiveMove(controller.getLocation().directionTo(loc));
    }

    // Util
    public static Integer travelDistance(MapLocation a, MapLocation b) {
        return Math.min(Math.abs(a.x-b.x), Math.abs(a.y-b.y));
    }

    public static boolean inMap(MapLocation a) {
        if (Cache.MAP_BOTTOM != 0 && Cache.MAP_BOTTOM > a.y) {
            return false;
        }
        if (Cache.MAP_LEFT != 0 && Cache.MAP_LEFT > a.x) {
            return false;
        }
        if (Cache.MAP_RIGHT != 0 && Cache.MAP_RIGHT < a.x) {
            return false;
        }
        if (Cache.MAP_TOP != 0 && Cache.MAP_TOP < a.y) {
            return false;
        }
        return true;
    }

    public static int[] relative (MapLocation from, MapLocation to) {
        return new int[] {to.x-from.x,to.y-from.y};
    }

    public static double calculateGradient(MapLocation start, MapLocation end) {
        if (end.x-start.x == 0) {
            return -1;
        }
        //Rise over run
        return (end.y-start.y)/(end.x-start.x);
    }

    //TODO: Make random take into account map areas
    public static MapLocation randomLocation() {
        MapLocation res = new MapLocation((int)(Math.random()*64- 32) + Cache.CURRENT_LOCATION.x,(int)(Math.random()*64-32) + Cache.CURRENT_LOCATION.y);
        while (!inMap(res)) {
            res = new MapLocation((int)(Math.random()*64- 32) + Cache.CURRENT_LOCATION.x,(int)(Math.random()*64-32) + Cache.CURRENT_LOCATION.y);
        }
        return res;
    }

}
