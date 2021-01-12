package teambot.battlecode2021;

import battlecode.common.*;
import teambot.RunnableBot;
import teambot.battlecode2021.util.Cache;
import teambot.battlecode2021.util.Communication;
import teambot.battlecode2021.util.Debug;
import teambot.battlecode2021.util.Pathfinding;

import java.util.Random;

public class PoliticanBot implements RunnableBot {
    private RobotController controller;
    private static Random random;
    private static boolean isTypeAttack;
    private static MapLocation EnemyEC;

    public PoliticanBot(RobotController controller) throws GameActionException {
        this.controller = controller;
        init();
    }

    @Override
    public void init() throws GameActionException {
        random = new Random(controller.getID());
        isTypeAttack = random.nextInt(4) < 3 ? true : false; //[0, 1, 2, 3] 75% attack, 25% defend

    }

    @Override
    public void turn() throws GameActionException {

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




}
