package mushroombot.battlecode2021;

import battlecode.common.*;
import mushroombot.RobotPlayer;
import mushroombot.RunnableBot;
import mushroombot.battlecode2021.util.*;

import java.util.*;

public class MuckrakerBot implements RunnableBot {
    private RobotController controller;
    private int order = 0;
    private MapLocation target;

    public MuckrakerBot(RobotController controller) {
        this.controller = controller;
        this.target = controller.getLocation();
    }

    @Override
    public void init() throws GameActionException {

    }


    @Override
    public void turn() throws GameActionException {

        /*
        if (Debug.debug) {
            System.out.println("I am robot " + controller.getType() + " at location " + controller.getLocation() + " and have cooldown " + controller.getCooldownTurns());
        }
        */
        // Find command center
        if (Cache.NUM_ROUNDS_SINCE_SPAWN == 1) {
            for (Map.Entry<MapLocation, Integer> entry : Cache.ALL_KNOWN_FRIENDLY_EC_LOCATIONS.entrySet()) {
                MapLocation loc = entry.getKey();
                Integer id = entry.getValue();
                // Is this my command center - 2 EC really close
                int[] sOrder = Communication.recieve(controller.getFlag(id));
                System.out.println(Arrays.toString(sOrder));
                if (sOrder[0] > 0 && sOrder[0] < 11) {
                    // Found command center
                    if (RobotPlayer.directionIntegerMap.get(loc.directionTo(controller.getLocation())) == sOrder[3]) {
                        Cache.COMMAND_LOCATION = loc;
                        Cache.COMMAND_ID = id;
                        order = sOrder[0];
                        // Setting scout location
                        if (order > 0 && order<9) {
                            Direction dir = RobotPlayer.directions[order - 1];
                            for (int i = 0; i < 64; i ++) {
                                target = target.add(dir);
                            }
                            order = 1;
                        }
                    }
                }
            }
        }

        int relativeX = Pathfinding.relative(Cache.COMMAND_LOCATION, Cache.CURRENT_LOCATION)[0];
        int relativeY = Pathfinding.relative(Cache.COMMAND_LOCATION, Cache.CURRENT_LOCATION)[1];

        if (simpleAttack()) {
            return;
        }
        switch (order) {
            case 0: //Camping
                break;
            case 1: //Moving|Scouting Map
                if (Cache.MAP_TOP>0&&Cache.MAP_BOTTOM>0&&Cache.MAP_LEFT>0&&Cache.MAP_RIGHT>0) {
                    order = 2;
                    target=Pathfinding.randomLocation();
                }
                else if (Pathfinding.move(target) == 2) {
                    System.out.println("Found edge of map!");
                    if (!controller.onTheMap(controller.adjacentLocation(Direction.NORTH))) {
                        Communication.trySend(20, 0, relativeY, 0);
                        Cache.MAP_TOP = Cache.CURRENT_LOCATION.y;
                    }
                    if (!controller.onTheMap(controller.adjacentLocation(Direction.EAST))) {
                        Communication.trySend(20, relativeX, 0, 2);
                        Cache.MAP_RIGHT = Cache.CURRENT_LOCATION.x;
                    }
                    if (!controller.onTheMap(controller.adjacentLocation(Direction.SOUTH))) {
                        Communication.trySend(20, 0, -relativeY, 4);
                        Cache.MAP_BOTTOM = Cache.CURRENT_LOCATION.y;
                    }
                    if (!controller.onTheMap(controller.adjacentLocation(Direction.WEST))) {
                        Communication.trySend(20, -relativeX, 0, 6);
                        Cache.MAP_LEFT = Cache.CURRENT_LOCATION.x;
                    }

                    // Now switch a target
                    if (Cache.MAP_TOP == 0) {
                        target = Cache.CURRENT_LOCATION.translate(0,64);
                    }
                    else if (Cache.MAP_BOTTOM == 0) {
                        target = Cache.CURRENT_LOCATION.translate(0,-64);
                    }
                    else if (Cache.MAP_RIGHT == 0) {
                        target = Cache.CURRENT_LOCATION.translate(64,0);
                    }
                    else if (Cache.MAP_LEFT == 0) {
                        target = Cache.CURRENT_LOCATION.translate(-64,0);
                    }
                }
                else {
                    return;
                };
            case 2:
                if (target.equals(Cache.CURRENT_LOCATION)) {
                    target = Pathfinding.randomLocation();
                }
                Pathfinding.move(target);
            default: 
                break;
        }
    }

    public boolean simpleAttack() throws GameActionException {
        Team enemy = controller.getTeam().opponent();
        int actionRadius = controller.getType().actionRadiusSquared;
        for (RobotInfo robot : controller.senseNearbyRobots(actionRadius, enemy)) {
            if (robot.type.canBeExposed()) {
                // It's a slanderer... go get them!
                if (controller.canExpose(robot.location)) {
                    System.out.println("e x p o s e d");
                    controller.expose(robot.location);
                    return true;
                }
            }
        }
        for (RobotInfo robot : Cache.ALL_NEARBY_ENEMY_ROBOTS) {
            if (robot.type.canBeExposed()) {
                Pathfinding.move(robot.location);
                return true;
            }
        }
        return false;
    }
}
