package teambot.battlecode2021;

import battlecode.common.*;
import teambot.RunnableBot;
import teambot.battlecode2021.util.*;

import java.util.*;

public class MuckrakerBot implements RunnableBot {
    private RobotController controller;
    private static Random random;
    private MapLocation scoutTarget;
    int relativeX;
    int relativeY;

    public MuckrakerBot(RobotController controller) {
        this.controller = controller;
    }

    @Override
    public void init() throws GameActionException {
        random = new Random(controller.getID());
        for (Map.Entry<MapLocation, Integer> entry : Cache.ALL_KNOWN_FRIENDLY_EC_LOCATIONS.entrySet()) {
            relativeX = Pathfinding.relative(entry.getKey(), Cache.CURRENT_LOCATION)[0];
            relativeY = Pathfinding.relative(entry.getKey(),Cache.CURRENT_LOCATION)[1];
            scoutTarget = new MapLocation(Cache.CURRENT_LOCATION.x - 64 * relativeX, Cache.CURRENT_LOCATION.y - 64 * relativeY);
            break;
        }
        if (Debug.debug) {
            System.out.println("relativeX " + relativeX + ", relativeY " + relativeY);
            System.out.println("Scout Target: " + scoutTarget);
        }
    }

    @Override
    public void turn() throws GameActionException {

        if (simpleAttack()) {
            if (Debug.debug) {
                System.out.println("Performed Simple Attack");
            }
            return;
        }

//        if ()



    }

    public boolean simpleAttack() throws GameActionException {
        Team enemy = Cache.OPPONENT_TEAM;
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
