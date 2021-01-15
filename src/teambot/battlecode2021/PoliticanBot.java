package teambot.battlecode2021;

import battlecode.common.*;
import teambot.RunnableBot;
import teambot.battlecode2021.util.Cache;
import teambot.battlecode2021.util.Communication;
import teambot.battlecode2021.util.Debug;
import teambot.battlecode2021.util.Pathfinding;

import java.util.Map;
import java.util.Random;

public class PoliticanBot implements RunnableBot {
    private RobotController controller;
    private static Random random;
    private Pathfinding pathfinding;
    private static boolean isTypeAttack;
    private static MapLocation EnemyEC;
    public static MapLocation neutralEC;

    private MapLocation[] friendlySlanderers;
//    private int[] friendlySlandererRobotIDs;

    public PoliticanBot(RobotController controller) throws GameActionException {
        this.controller = controller;
        init();
    }

    @Override
    public void init() throws GameActionException {
        this.pathfinding = new Pathfinding();
        pathfinding.init(controller);

        random = new Random(controller.getID());
        isTypeAttack = random.nextInt(4) < 3 ? true : false; //[0, 1, 2, 3] 75% attack, 25% defend
        friendlySlanderers = new MapLocation[60];
//        friendlySlandererRobotIDs = new int[999];

    }

    @Override
    public void turn() throws GameActionException {
        if (moveAndDestoryNeutralEC()) {
            Debug.printInformation("Moving to destory netural EC", "");
            return;
        }
        if (chaseMuckrakerUntilExplode()) {
            Debug.printInformation("CHASING MUCKRAKER ", "");
            return;
        } else {
            Pathfinding.move(Pathfinding.randomLocation());
        }

        if (isTypeAttack) {
            if (attackingPoliticianNearEnemyEC()) {
                if (Debug.debug) {
                    System.out.println("ATTACKING POLITICIAN NEAR ENEMY EC -- ATTACK");
                }

                if (!controller.isReady()) {
                    return;
                }

                if (explodeEC(EnemyEC)) {

                }
                return;
            }
        }
    }

    public boolean leaveBaseToEnterLattice() throws GameActionException {

        return false;
    }

    public boolean moveAndDestoryNeutralEC() throws GameActionException {
        if (neutralEC == null) {
            if (Debug.debug) {
                System.out.println("neutralEC not set");
            }
            return false;
        }
        int actionRadius = controller.getType().actionRadiusSquared;
        RobotInfo[] nearbyRobots = controller.senseNearbyRobots(actionRadius);
        for (RobotInfo robotInfo : nearbyRobots) {
            if (robotInfo.type == RobotType.ENLIGHTENMENT_CENTER && robotInfo.team == Team.NEUTRAL) {
                // explode if no other nearby robots
                if (nearbyRobots.length == 1 && controller.canEmpower(actionRadius)) {
                    controller.empower(actionRadius);
                    return true;
                }
            }
        }
        int flag = Communication.POLITICIAN_ATTACK_FLAG;
        if (controller.canSetFlag(flag)) {
            controller.setFlag(flag);
        }
        pathfinding.move(neutralEC);
        return true;
    }

    //chasing flag -> flagType | robotID
    public boolean chaseMuckrakerUntilExplode() throws GameActionException {

        int friendlySlandersSize = 0;

        for (RobotInfo robotInfo : Cache.ALL_NEARBY_FRIENDLY_ROBOTS) {
            if (robotInfo.type == RobotType.POLITICIAN) {
                int encodedFlag = Communication.checkAndGetFlag(robotInfo.ID);
                if (Communication.decodeIsFlagLocationType(encodedFlag, true)) {
                    if (Communication.decodeExtraData(encodedFlag) == 7) {
                        // This is a slanderer on our team...
                        friendlySlanderers[friendlySlandersSize++] = robotInfo.location;
                    }
                }
            }
        }

        MapLocation toTarget = null;
        int leastDistance = 9999;
        for (RobotInfo robotInfo : Cache.ALL_NEARBY_ENEMY_ROBOTS) {
            if (robotInfo.type == RobotType.MUCKRAKER) {
                int minDistance = 9998;
                //enemy muckraker
                //TODO: make sure that no friendly unit is already tracking the muckraker
                //TODO: leave the muckraker alone if it goes far enough from our defense location

                //find minimum distance from slanderers to enemy
                for (int i = 0; i < friendlySlandersSize; ++i) {
                    minDistance = Math.min(minDistance, robotInfo.location.distanceSquaredTo(friendlySlanderers[i]));
                }
                if (leastDistance > minDistance) {
                    leastDistance = minDistance;
                    toTarget = robotInfo.location;
                }

            }
        }

        if (toTarget != null && controller.isReady()) {
            //explode?
            if (leastDistance <= RobotType.MUCKRAKER.actionRadiusSquared + 1) {
                controller.empower(RobotType.POLITICIAN.actionRadiusSquared);
                return true;
            }
            if (false) {

            }
            Pathfinding.move(toTarget);
            return true;
        }

        return false;
    }

    //ASSUME POLITICIAN CAN PATHFIND TO EC LOCATION

    public boolean attackingPoliticianNearEnemyEC() throws GameActionException {
        for (RobotInfo robot : Cache.ALL_NEARBY_FRIENDLY_ROBOTS) {
            if (robot.getType() == RobotType.ENLIGHTENMENT_CENTER) {
                int flag = Communication.POLITICIAN_ATTACK_FLAG;
                if (controller.canSetFlag(flag)) {
                    controller.setFlag(flag);
                    EnemyEC = robot.getLocation();
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
