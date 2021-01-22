package teambot;
import battlecode.common.*;
import teambot.battlecode2021.EnlightenmentCenterBot;
import teambot.battlecode2021.MuckrakerBot;
import teambot.battlecode2021.PoliticanBot;
import teambot.battlecode2021.SlandererBot;
import teambot.battlecode2021.util.*;

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

        Util.init(controller);
        Cache.init(controller);
        Debug.init(controller);
        Scout.init(controller);
        Comms.init(controller);

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

        boolean errored = false;
        while (true) {
            try {
                while (true) {
                    if (errored) {
                        // RED
                        controller.setIndicatorDot(controller.getLocation(),255,0,0);
                    }
                    int currentTurn = controller.getRoundNum(); //starts at round 1

//                    Debug.resignGame(500);
                   // if (controller.getRoundNum() == 1000) controller.resign();
                    if (Cache.ROBOT_TYPE != controller.getType()) {
                        bot = new PoliticanBot(controller);
                        Cache.ROBOT_TYPE = controller.getType();
                    }
                    Util.loop();
                    runAwayFromAttackFlag();
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

    private static void runAwayFromAttackFlag() throws GameActionException {

        if (controller.getType() == RobotType.ENLIGHTENMENT_CENTER || !controller.isReady()) return;

        //(1/20 DONE): changed this function so that the politician with the lowest health (break health ties by ID) is used to attack first (does not execute moveAwayFromLocation)

        //find which poli to avoid (which one is planning on attacking)
        MapLocation robotToAvoid = null;
        int robotToAvoidHealth = 99999999;
        int robotToAvoidID = 999999999;

        /* Find the closest EC */
        MapLocation ecLocationGuess = null;
        int ecDistanceGuess = 99999999;

        for (RobotInfo nearbyAlliedRobot : Cache.ALL_NEARBY_FRIENDLY_ROBOTS) {
            if (controller.canGetFlag(nearbyAlliedRobot.ID)) {
                int encodedFlag = controller.getFlag(nearbyAlliedRobot.ID);

                /* Find the best politician to avoid */
                boolean ToMoveAwayFromThisPolitician = CommunicationECDataSmall.decodeIsSchemaType(encodedFlag)
                        && CommunicationECDataSmall.decodeIsMoveAwayFromMe(encodedFlag);
                ToMoveAwayFromThisPolitician |= CommunicationMovement.decodeIsSchemaType(encodedFlag)
                        && CommunicationMovement.decodeCommunicationToOtherBots(encodedFlag) == CommunicationMovement.COMMUNICATION_TO_OTHER_BOTS.MOVE_AWAY_FROM_ME;
                System.out.println("move away? " + ToMoveAwayFromThisPolitician);
                if (ToMoveAwayFromThisPolitician) {
                    if (nearbyAlliedRobot.conviction < robotToAvoidHealth) {
                        robotToAvoid = nearbyAlliedRobot.location;
                        robotToAvoidHealth = nearbyAlliedRobot.conviction;
                        robotToAvoidID = nearbyAlliedRobot.ID;
                    } else if (nearbyAlliedRobot.conviction == robotToAvoidHealth && nearbyAlliedRobot.ID < robotToAvoidID) {
                        robotToAvoid = nearbyAlliedRobot.location;
                        robotToAvoidHealth = nearbyAlliedRobot.conviction;
                        robotToAvoidID = nearbyAlliedRobot.ID;
                    }
                }
            }

            if (nearbyAlliedRobot.type == RobotType.ENLIGHTENMENT_CENTER && nearbyAlliedRobot.team != Cache.OUR_TEAM) {
                int ecDist = Cache.CURRENT_LOCATION.distanceSquaredTo(nearbyAlliedRobot.location);
                if (ecDist < ecDistanceGuess) {
                    ecDistanceGuess = ecDist;
                    ecLocationGuess = nearbyAlliedRobot.location;
                }
            }
        }

        Debug.printInformation("robotToAvoid " + robotToAvoid, " ? ");

        if (robotToAvoid == null || Cache.CURRENT_LOCATION.distanceSquaredTo(robotToAvoid) >= RobotType.POLITICIAN.actionRadiusSquared) {
            return;
        }

        if (Cache.ROBOT_TYPE != RobotType.POLITICIAN) {
            moveAwayFromLocation(robotToAvoid, ecLocationGuess);
            return;
        }

        /* Check if I am an attacking politician better off to avoid */
        if (Cache.ROBOT_TYPE == RobotType.POLITICIAN && Cache.EC_INFO_ACTION == CommunicationECSpawnFlag.ACTION.ATTACK_LOCATION) {
            if (robotToAvoidHealth < Cache.CONVICTION) {
                //I have more health than the best politician, so I will move out of the way
                moveAwayFromLocation(robotToAvoid, ecLocationGuess);
                return;
            }
            if (robotToAvoidHealth == Cache.CONVICTION && Cache.ID > robotToAvoidID) {
                moveAwayFromLocation(robotToAvoid, ecLocationGuess);
                return;
            }
        }
    }


    private static int addedLocationDistance(MapLocation one, MapLocation two) {
        return Math.abs(one.x - two.x) + Math.abs(one.y - two.y);
    }

    private static boolean moveAwayFromLocation(MapLocation avoidLocation, MapLocation ecToAvoid) throws GameActionException {

        if (!controller.isReady()) return false;

        int maximizedDistance = addedLocationDistance(Cache.CURRENT_LOCATION, avoidLocation);

        if (Cache.CURRENT_LOCATION.distanceSquaredTo(avoidLocation) > RobotType.POLITICIAN.actionRadiusSquared + 3) return false;

        Direction maximizedDirection = null;
        MapLocation maximizedLocation = null;

        for (Direction direction : Constants.DIRECTIONS) {
            if (controller.canMove(direction)) {
                MapLocation candidateLocation = Cache.CURRENT_LOCATION.add(direction);
                int candidateDistance = addedLocationDistance(candidateLocation, avoidLocation);
                if (candidateDistance > maximizedDistance) {
                    maximizedDistance = candidateDistance;
                    maximizedDirection = direction;
                    maximizedLocation = candidateLocation;
                } else if (candidateDistance == maximizedDistance && ecToAvoid != null) {
                    int candidateDistToEC = candidateLocation.distanceSquaredTo(ecToAvoid);
                    int currentBestDistToEC = maximizedLocation.distanceSquaredTo(ecToAvoid);
                    Debug.printInformation("DEBUG MOVE AWAY -> candidate distance " + candidateDistToEC + ", current distance " + currentBestDistToEC + " ", " VERIFY? ");
                    if (candidateDistToEC > currentBestDistToEC) {
                        maximizedDistance = candidateDistance;
                        maximizedDirection = direction;
                        maximizedLocation = candidateLocation;
                    }
                }
            }
        }

        if (maximizedDirection != null) {
            controller.move(maximizedDirection);
            return true;
        }

        return false;
    }

}
