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
    private static boolean isBlockingEnemyEC;

    public MuckrakerBot(RobotController controller) throws GameActionException {
        this.controller = controller;
        init();
    }

    @Override
    public void init() throws GameActionException {

        Debug.printByteCode("INIT START");

        random = new Random(controller.getID());
        isBlockingEnemyEC = false;

        for (Map.Entry<MapLocation, Integer> entry : Cache.ALL_KNOWN_FRIENDLY_EC_LOCATIONS.entrySet()) {
            relativeX = Pathfinding.relative(entry.getKey(), Cache.CURRENT_LOCATION)[0];
            relativeY = Pathfinding.relative(entry.getKey(),Cache.CURRENT_LOCATION)[1];
            scoutTarget = new MapLocation(Cache.CURRENT_LOCATION.x - 64 * relativeX, Cache.CURRENT_LOCATION.y - 64 * relativeY);
            break;
        }


        Debug.printByteCode("INIT END");
    }

    @Override
    public void turn() throws GameActionException {
        Debug.printByteCode("TURN START");

        if (Debug.debug) {
            System.out.println("relativeX " + relativeX + ", relativeY " + relativeY);
            System.out.println("Scout Target: " + scoutTarget);
//            Pathfinding.move()
        }


        if (simpleAttack()) {
            if (Debug.debug) {
                System.out.println("Performed Simple Attack");
            }
            return;
        }

        isBlockingEnemyEC = updateBlockingEnemyEC();

        if (attackingPoliticianNearEnemyEC()) {
            if (Debug.debug) {
                System.out.println("POLITICAN NEAR SCOUT THAT IS NEAR ENEMY EC -- RUN AWAY");
            }
            return;
        }

        Debug.printByteCode("TURN END");
//        if ()
    }

    //assume robot always tries to surround/get as close as possible to a EC (only 1 distance, extras can roam around to edge map/bounce around)

    public boolean updateBlockingEnemyEC() {
        for (RobotInfo robot : Cache.ALL_NEARBY_ENEMY_ROBOTS) {
            if (robot.getType() == RobotType.ENLIGHTENMENT_CENTER) {
                return true;
            }
        }
        return false;
    }

    public boolean attackingPoliticianNearEnemyEC() throws GameActionException {
        boolean runAway = false;

        if (!isBlockingEnemyEC) {
            return runAway;
        }

        MapLocation politicianLocation = null;
        for (RobotInfo robot : Cache.ALL_NEARBY_FRIENDLY_ROBOTS) {
            if (robot.getType() == RobotType.POLITICIAN) {
                if (controller.canGetFlag(robot.ID)) {
                    int flag = controller.getFlag(robot.ID);
                    if (Communication.isPoliticianAttackingFlag(flag)) {
                        politicianLocation = robot.getLocation();
                        runAway = true;
                    }
                }
            }
        }

        //TODO: implement runAway
        if (runAway) {
            int relativeX = Pathfinding.relative(politicianLocation, Cache.CURRENT_LOCATION)[0];
            int relativeY = Pathfinding.relative(politicianLocation, Cache.CURRENT_LOCATION)[1];
            // go opposite direction of politician
        }

        return runAway;
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
