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
    private boolean hasSetFlag = false;

    public MuckrakerBot(RobotController controller) {
        this.controller = controller;
        this.target = controller.getLocation();
    }

    @Override
    public void init() throws GameActionException {

    }


    @Override
    public void turn() throws GameActionException {
        if (Debug.debug) {
            System.out.println("I am robot " + controller.getType() + " at location " + controller.getLocation() + " and have cooldown " + controller.getCooldownTurns());
        }
        // Find command center
        System.out.println(Cache.NUM_ROUNDS_SINCE_SPAWN);
        if (Cache.NUM_ROUNDS_SINCE_SPAWN == 1) {
            for (Map.Entry<MapLocation, Integer> entry : Cache.ALL_KNOWN_FRIENDLY_EC_LOCATIONS.entrySet()) {
                MapLocation loc = entry.getKey();
                Integer id = entry.getValue();
                // Is this my command center - 2 EC really close
                int[] sOrder = Communication.recieve(controller.getFlag(id));
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

        if (simpleAttack()) {
            return;
        }
        switch (order) {
            case 0: //Camping
                break;
            case 1: //Moving|Scouting
                if (Pathfinding.move(target) == 2 && !hasSetFlag) {
                    System.out.println("Found edge of map!");
                };
            default: //Random Move
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
