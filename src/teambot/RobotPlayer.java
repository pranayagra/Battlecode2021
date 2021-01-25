package teambot;
import battlecode.common.*;
import teambot.battlecode2021.EnlightenmentCenterBot;
import teambot.battlecode2021.MuckrakerBot;
import teambot.battlecode2021.PoliticanBot;
import teambot.battlecode2021.SlandererBot;
import teambot.battlecode2021.util.*;

public strictfp class RobotPlayer {
    public static RobotController controller;

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
                Spawning.init(controller);
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
        boolean bytecodeError = false;
        int bytecodeErrorCount = 0;
        while (true) {
            try {
                while (true) {
                    if (errored) {
                        // WHITE
                        Debug.printByteCode("ERROR --> THREW AN EXCEPTION OR SOMETHING (BLACK) " + controller.getLocation());
                        controller.setIndicatorDot(controller.getLocation(),0, 0,0);
                    }
                    if (bytecodeError) {
                        // MAGENTA
                        Debug.printByteCode("ERROR --> BYTECODE RAN OUT (MAGENTA) " + controller.getLocation() + " HAS HAPPENED " + bytecodeErrorCount + " TIMES ");
                        controller.setIndicatorDot(controller.getLocation(),255,0,255);
                    }
                    int currentTurn = controller.getRoundNum(); //starts at round 1

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
                        bytecodeErrorCount++;
                        controller.setIndicatorDot(controller.getLocation(),255,0,255);
                        bytecodeError = true;
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

        //find the closest poli to avoid
        MapLocation robotToAvoid = null;
        int robotToAvoidDistance = 9999999;
        int robotToAvoidHealth = 99999999;

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

                if (ToMoveAwayFromThisPolitician) {

                    int candidateDistance = Cache.CURRENT_LOCATION.distanceSquaredTo(nearbyAlliedRobot.location);

                    /* I should avoid the closest robot with the flag. If there is a tie, avoid the robot with the most health. */
                    if (candidateDistance < robotToAvoidDistance) {
                        robotToAvoid = nearbyAlliedRobot.location;
                        robotToAvoidDistance = candidateDistance;
                        robotToAvoidHealth = nearbyAlliedRobot.conviction;
                    } else if (candidateDistance == robotToAvoidDistance) {
                        if (nearbyAlliedRobot.conviction > robotToAvoidHealth) {
                            robotToAvoid = nearbyAlliedRobot.location;
                            robotToAvoidDistance = candidateDistance;
                            robotToAvoidHealth = nearbyAlliedRobot.conviction;
                        }
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

        if (robotToAvoid == null || Cache.CURRENT_LOCATION.distanceSquaredTo(robotToAvoid) >= RobotType.POLITICIAN.actionRadiusSquared) {
            return;
        }

        if (Cache.ROBOT_TYPE != RobotType.POLITICIAN) {
            moveAwayFromLocation(robotToAvoid, ecLocationGuess);
            return;
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
