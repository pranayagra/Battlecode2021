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
                    // if (controller.getRoundNum() == 500) controller.resign();
                    if (!Cache.ROBOT_TYPE.equals(controller.getType())) {
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

        for (RobotInfo nearbyAlliedRobot : Cache.ALL_NEARBY_FRIENDLY_ROBOTS) {
            if (controller.canGetFlag(nearbyAlliedRobot.ID)) {
                int encodedFlag = controller.getFlag(nearbyAlliedRobot.ID);
                boolean moveAway = false;
                if (CommunicationECDataSmall.decodeIsSchemaType(encodedFlag)) {
                    moveAway = CommunicationECDataSmall.decodeIsMoveAwayFromMe(encodedFlag);
                }
                if (CommunicationMovement.decodeIsSchemaType(encodedFlag) && CommunicationMovement.decodeCommunicationToOtherBots(encodedFlag) == CommunicationMovement.COMMUNICATION_TO_OTHER_BOTS.MOVE_AWAY_FROM_ME) {
                    moveAway = true;
                }
//                    CommunicationMovement.COMMUNICATION_TO_OTHER_BOTS otherBotIsTellingMeTo = CommunicationMovement.decodeCommunicationToOtherBots(encodedFlag);
                if (moveAway) moveAwayFromLocation(nearbyAlliedRobot.location);
//                    switch (moveAway) {
//                        case MOVE_AWAY_FROM_ME:
//                            moveAwayFromLocation(nearbyAlliedRobot.location);
//                            break;
//                        case MOVE_TOWARDS_ME:
//                            break;
//
            }
        }
    }


    private static int addedLocationDistance(MapLocation one, MapLocation two) {
        return Math.abs(one.x - two.x) + Math.abs(one.y - two.y);
    }

    private static boolean moveAwayFromLocation(MapLocation avoidLocation) throws GameActionException {

        if (!controller.isReady()) return false;

        int maximizedDistance = addedLocationDistance(Cache.CURRENT_LOCATION, avoidLocation);

        if (Cache.CURRENT_LOCATION.distanceSquaredTo(avoidLocation) > RobotType.POLITICIAN.actionRadiusSquared + 3) return false;

        Direction maximizedDirection = null;

        for (Direction direction : Constants.DIRECTIONS) {
            if (controller.canMove(direction)) {
                MapLocation candidateLocation = Cache.CURRENT_LOCATION.add(direction);
                int candidateDistance = addedLocationDistance(candidateLocation, avoidLocation);
                if (candidateDistance > maximizedDistance) {
                    maximizedDistance = candidateDistance;
                    maximizedDirection = direction;
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
