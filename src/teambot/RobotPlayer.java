package teambot;
import battlecode.common.*;
import teambot.battlecode2021.EnlightenmentCenterBot;
import teambot.battlecode2021.MuckrakerBot;
import teambot.battlecode2021.PoliticanBot;
import teambot.battlecode2021.SlandererBot;
import teambot.battlecode2021.util.Debug;
import teambot.battlecode2021.util.Util;

public strictfp class RobotPlayer {
    public static RobotController controller;

    public static final RobotType[] spawnableRobot = {
        RobotType.POLITICIAN,
        RobotType.SLANDERER,
        RobotType.MUCKRAKER,
    };

    public static final Direction[] directions = {
        Direction.NORTH,
        Direction.NORTHEAST,
        Direction.EAST,
        Direction.SOUTHEAST,
        Direction.SOUTH,
        Direction.SOUTHWEST,
        Direction.WEST,
        Direction.NORTHWEST,
    };

    public static int turnCount;

    /**
     * run() is the method that is called when a robot is instantiated in the Battlecode world.
     * If this method returns, the robot dies!
     **/
    @SuppressWarnings("unused")
    public static void run(RobotController controller) throws GameActionException {

        // This is the RobotController object. You use it to perform actions from this robot,
        // and to get information on its current status.
        RobotPlayer.controller = controller;
        RunnableBot bot;
        switch (controller.getType()) {
            case ENLIGHTENMENT_CENTER:
                bot = new EnlightenmentCenterBot(controller);
                break;
            case POLITICIAN:
                bot = new PoliticanBot(controller);
                break;
            case SLANDERER:
                bot = new SlandererBot(controller);
                break;
            case MUCKRAKER:
                bot = new MuckrakerBot(controller);
                break;
            default:
                throw new IllegalStateException("NOT A VALID BOT");
        }

        if (Debug.debug) {
            System.out.println("I am robot " + controller.getType() + " at location " + controller.getLocation() + " and have cooldown " + controller.getCooldownTurns());
            System.out.println("Influence " + controller.getInfluence() + ", Conviction " + controller.getConviction());
        }

        Util.init(controller);

        boolean errored = false;
        while (true) {
            try {
                while (true) {
                    if (errored) {
                        // RED
                        controller.setIndicatorDot(controller.getLocation(),255,0,0);
                    }
                    int currentTurn = controller.getRoundNum(); //starts at round 1
                    Util.loop();
                    bot.turn();
                    Util.postLoop();
                    if (controller.getRoundNum() != currentTurn) {
                        //Ran out of bytecodes - MAGENTA color debug
                        controller.setIndicatorDot(controller.getLocation(),255,0,255);
                    }
                    Clock.yield();
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                errored = true;
                Clock.yield();
            }
        }

    }

//    static void runEnlightenmentCenter() throws GameActionException {
//        RobotType toBuild = randomSpawnableRobotType();
//        int influence = 50;
//        for (Direction dir : directions) {
//            if (rc.canBuildRobot(toBuild, dir, influence)) {
//                rc.buildRobot(toBuild, dir, influence);
//            } else {
//                break;
//            }
//        }
//    }
//
//    static void runPolitician() throws GameActionException {
//        Team enemy = rc.getTeam().opponent();
//        int actionRadius = rc.getType().actionRadiusSquared;
//        RobotInfo[] attackable = rc.senseNearbyRobots(actionRadius, enemy);
//        if (attackable.length != 0 && rc.canEmpower(actionRadius)) {
//            System.out.println("empowering...");
//            rc.empower(actionRadius);
//            System.out.println("empowered");
//            return;
//        }
//        if (tryMove(randomDirection()))
//            System.out.println("I moved!");
//    }
//
//    static void runSlanderer() throws GameActionException {
//        if (tryMove(randomDirection()))
//            System.out.println("I moved!");
//    }
//
//    static void runMuckraker() throws GameActionException {
//        Team enemy = rc.getTeam().opponent();
//        int actionRadius = rc.getType().actionRadiusSquared;
//        for (RobotInfo robot : rc.senseNearbyRobots(actionRadius, enemy)) {
//            if (robot.type.canBeExposed()) {
//                // It's a slanderer... go get them!
//                if (rc.canExpose(robot.location)) {
//                    System.out.println("e x p o s e d");
//                    rc.expose(robot.location);
//                    return;
//                }
//            }
//        }
//        if (tryMove(randomDirection()))
//            System.out.println("I moved!");
//    }

    /**
     * Returns a random Direction.
     *
     * @return a random Direction
     */
    static Direction randomDirection() {
        return directions[(int) (Math.random() * directions.length)];
    }

    /**
     * Returns a random spawnable RobotType
     *
     * @return a random RobotType
     */
    static RobotType randomSpawnableRobotType() {
        return spawnableRobot[(int) (Math.random() * spawnableRobot.length)];
    }

    /**
     * Attempts to move in a given direction.
     *
     * @param dir The intended direction of movement
     * @return true if a move was performed
     * @throws GameActionException
     */
    static boolean tryMove(Direction dir) throws GameActionException {
        System.out.println("I am trying to move " + dir + "; " + controller.isReady() + " " + controller.getCooldownTurns() + " " + controller.canMove(dir));
        if (controller.canMove(dir)) {
            controller.move(dir);
            return true;
        } else return false;
    }
}
