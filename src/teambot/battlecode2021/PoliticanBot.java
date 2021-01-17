package teambot.battlecode2021;

import battlecode.common.*;
import teambot.RunnableBot;
import teambot.battlecode2021.util.*;

import java.util.Arrays;
import java.util.Map;
import java.util.Random;

public class PoliticanBot implements RunnableBot {
    private RobotController controller;
    private static Random random;
    private Pathfinding pathfinding;
    private static boolean isTypeAttack;
    private static MapLocation EnemyEC;
    public static MapLocation neutralEC;

    private static int ECFlagForAttackBot;

    private MapLocation[] friendlySlanderers;
//    private int[] friendlySlandererRobotIDs;

    //TODO: Politican bot upgrade movement (currently bugged and random movement) + fix explosion bug/optimize explosion radius

    public PoliticanBot(RobotController controller) throws GameActionException {
        this.controller = controller;
        init();
    }

    @Override
    public void init() throws GameActionException {
        this.pathfinding = new Pathfinding();
        pathfinding.init(controller);

        random = new Random(controller.getID());

        if (Cache.CONVICTION % 50 == 3) { //53, 103, 153, etc influence are attackPoliticanBotsOnECFlagLocation
            isTypeAttack = true;
            if (controller.canGetFlag(Cache.myECID)) {
                ECFlagForAttackBot = controller.getFlag(Cache.myECID);
            }
        }

        friendlySlanderers = new MapLocation[80];
//        friendlySlandererRobotIDs = new int[999];

    }

    @Override
    public void turn() throws GameActionException {
//        if (moveAndDestoryNeutralEC()) {
//            Debug.printInformation("Moving to destory netural EC", "");
//            return;
//        }
        if (chaseMuckrakerUntilExplode()) {
            Debug.printInformation("CHASING MUCKRAKER ", "");
            return;
        } else if (controller.isReady()) {
            Debug.printInformation("RANDOM MOVE ", "");
            Pathfinding.move(Pathfinding.randomLocation());
            return;
        }

//        if (isTypeAttack) {
//            if (attackingPoliticianNearEnemyEC()) {
//                if (Debug.debug) {
//                    System.out.println("ATTACKING POLITICIAN NEAR ENEMY EC -- ATTACK");
//                }
//
//                if (!controller.isReady()) {
//                    return;
//                }
//
//                if (explodeEC(EnemyEC)) {
//
//                }
//                return;
//            }
//        }
    }

    public boolean leaveBaseToEnterLattice() throws GameActionException {

        return false;
    }


    //Assumptions: neutralEC is found by a scout and communicated to the EC
    //Bugs:
    //  friendly units nearby our base may move around as a result of the flag. We should have a "no matter what I will not move" flag to avoid this danger situation (especially with wall units) and only set the flag when once
    //  What if two ECs close together? Then nearbyRobots.length == 1 is always false (I think this condition is too risky regardless of this situation)

    //TODO: I think protocol should be ->
    // EC spawns politician (maybe let's say xx2 influence politicians are type neutral EC explode) and let's this bot know the location of the neutralEC
    // Then this bot pathfinds to the location, waits until it is somewhat close, sets its flag to attacking, and then explodes based on condition (hard part)

    // Enhancements:
    //      the "actionRadius" is not always the most preferred explosion radius. Rather, smaller is usually better here
    //      the nearbyRobots.length == 1 is too strict (we cannot force enemy robots to leave). Rather, the politican should be able to the best location
    //      we only explode if it is a 1-shot (with some extra for health). If it is no longer a 1-shot, this politican is repurposed.
    //      check for enemy politicians?
    //      We may want a way to clear enemies that just surround the neutral EC with weak targets but do not capture
    public boolean moveAndDestoryNeutralEC() throws GameActionException {

        if (neutralEC == null) {
            if (Debug.debug) {
                System.out.println("neutralEC not set");
            }
            return false;
        }

        int actionRadius = Cache.ROBOT_TYPE.actionRadiusSquared;
        RobotInfo[] nearbyRobots = controller.senseNearbyRobots(actionRadius);
        for (RobotInfo robotInfo : nearbyRobots) {
            if (robotInfo.type == RobotType.ENLIGHTENMENT_CENTER && robotInfo.team == Team.NEUTRAL) {
                // explode if no other nearby robots
                //TODO: pranay's comment -> I think 1 is too risky since the enemy might have a bot nearby? not sure...
                if (nearbyRobots.length == 1 && controller.canEmpower(actionRadius)) {
                    controller.empower(actionRadius);
                    return true;
                }
            }
        }
        int flag = CommunicationMovement.encodeMovement(false, true,
                CommunicationMovement.MY_UNIT_TYPE.PO, CommunicationMovement.MOVEMENT_BOTS_DATA.NOOP,
                CommunicationMovement.COMMUNICATION_TO_OTHER_BOTS.NOOP, false, false, 0);
        Comms.checkAndAddFlag(flag);

        pathfinding.move(neutralEC);
        return true;
    }

    //chasing flag -> flagType | robotID
    // has some bugs! Pranay's Method...
    // enhancements:
    //      stick to a certain distance away from slanderers or better yet muckrakers (outside the wall) instead of random movement
    //      only focus on enemy politicians maybe? since muckrakers can't get through the wall anyways... risky
    //      explosion radius
    //      sometimes we move which causes us to not be able to cast ability until later
    //      there are some bugs with not exploding at the right time, or multiple bots exloding at the same time
    //      
    public boolean chaseMuckrakerUntilExplode() throws GameActionException {

        int friendlySlanderersSize = 0;
//        System.out.println();

        for (RobotInfo robotInfo : Cache.ALL_NEARBY_FRIENDLY_ROBOTS) {
            if (robotInfo.type == RobotType.POLITICIAN) {
                if (controller.canGetFlag(robotInfo.ID)) {
                    int encodedFlag = controller.getFlag(robotInfo.ID);
                    if (Debug.debug) System.out.println("flag is " + encodedFlag + " with location " + robotInfo.location);
                    if (CommunicationMovement.decodeIsSchemaType(encodedFlag) && CommunicationMovement.decodeMyUnitType(encodedFlag) == CommunicationMovement.MY_UNIT_TYPE.SL) {
                        // This is a slanderer on our team...
                        friendlySlanderers[friendlySlanderersSize++] = robotInfo.location;
                    }
                }
            }
        }


        Debug.printInformation("friendlySlanderers Size ", friendlySlanderersSize);
        Debug.printInformation("friendlySlanderers ", Arrays.toString(friendlySlanderers));

        MapLocation toTarget = null;
        int leastDistance = 9999;
        for (RobotInfo robotInfo : controller.senseNearbyRobots(-1, Cache.OPPONENT_TEAM)) {
            if (robotInfo.type == RobotType.MUCKRAKER) {
                int minDistance = 9998;
                //enemy muckraker
                //TODO: make sure that no friendly unit is already tracking the muckraker -- maybe don't implement this, not high reward and risky..
                //TODO: leave the muckraker alone if it goes far enough from our defense location
                if (Debug.debug) System.out.println("found enemy MUCKRAKER at " + robotInfo.location);
                //find minimum distance from slanderers to enemy
                for (int i = 0; i < friendlySlanderersSize; ++i) {
                    minDistance = Math.min(minDistance, robotInfo.location.distanceSquaredTo(friendlySlanderers[i]));
                }
                if (leastDistance > minDistance) {
                    leastDistance = minDistance;
                    toTarget = robotInfo.location;
                }

            }
        }

        Debug.printInformation("Minimum distance enemy muckraker is to slanderer is ", leastDistance);
        Debug.printInformation("and targeting location ", toTarget);


        if (toTarget != null && controller.isReady()) {
            //explode?
            if (leastDistance <= RobotType.MUCKRAKER.actionRadiusSquared + 1) {
                // our slanderer is in danger!!
                if (controller.canEmpower(Cache.CURRENT_LOCATION.distanceSquaredTo(toTarget))) {
                    controller.empower(Cache.CURRENT_LOCATION.distanceSquaredTo(toTarget));
                    return true;
                }
            }
            Pathfinding.move(toTarget);
            return true;
        }

        return false;
    }

    //ASSUME POLITICIAN CAN PATHFIND TO EC LOCATION

    public boolean attackingPoliticianNearEnemyEC() throws GameActionException {
        for (RobotInfo robot : Cache.ALL_NEARBY_ROBOTS) {
            if (robot.getType() == RobotType.ENLIGHTENMENT_CENTER) {
                int flag = CommunicationMovement.encodeMovement(
                        true,true,CommunicationMovement.MY_UNIT_TYPE.PO, CommunicationMovement.MOVEMENT_BOTS_DATA.IN_DANGER_MOVE,
                        CommunicationMovement.COMMUNICATION_TO_OTHER_BOTS.MOVE_AWAY_FROM_ME, false,true,0);
                if (controller.canSetFlag(flag)) {
                    controller.setFlag(flag); //CARE ABOUT SEED LATER? //NOTE THIS IS SETFLAG BECAUSE WE DO NOT WANT TO QUEUE IT BUT SKIP QUEUE AND SET FLAG
                    EnemyEC = robot.getLocation();
                    Comms.hasSetFlag = true;
                    return true;
                }
            }
        }
        return false;
    }

    public boolean explodeEC(MapLocation EC) throws GameActionException {

        int distance = Pathfinding.travelDistance(EC, Cache.CURRENT_LOCATION);
        int actionRadius = RobotType.POLITICIAN.actionRadiusSquared;

        if (controller.canSenseRadiusSquared(distance)) {
            int NUM_SCOUTS = 0;
            for (RobotInfo robot : controller.senseNearbyRobots(distance, Cache.OUR_TEAM)) {
                if (robot.getType() == RobotType.MUCKRAKER) {
                    ++NUM_SCOUTS;
                }
            }
            System.out.println("NUM OF SCOUTS IN DISTANCE " + NUM_SCOUTS);
            if (NUM_SCOUTS > 0) {
                //TODO: MOVE CLOSER
                return false;
            }

            int damage = getExplosionDamage(distance);
            //TODO: when to explode?
            if (true) {
                if (controller.canEmpower(distance)) {
                    controller.empower(distance);
                    return true;
                }
            }

        }

        return false;
    }

    public int getExplosionDamage(int radius) {
        int total_damage = Math.max(Cache.CONVICTION - 10, 0);
        int num_robots = controller.senseNearbyRobots(radius).length;
        int damage = total_damage / num_robots;
        return damage;
    }

    public void followMuckraker() {

    }




}
