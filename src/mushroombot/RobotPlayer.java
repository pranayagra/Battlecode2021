package mushroombot;
import java.util.HashMap;
import java.util.Map;

import battlecode.common.*;
import mushroombot.battlecode2021.util.*;
import mushroombot.battlecode2021.*;
import mushroombot.battlecode2021.EnlightenmentCenterBot;
import mushroombot.battlecode2021.MuckrakerBot;
import mushroombot.battlecode2021.PoliticanBot;
import mushroombot.battlecode2021.SlandererBot;
import mushroombot.battlecode2021.util.Debug;

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

    public static Map<Direction,Integer> directionIntegerMap = new HashMap<Direction,Integer>() {{
        put(Direction.NORTH,0);
        put(Direction.NORTHEAST,1);
        put(Direction.EAST,2);
        put(Direction.SOUTHEAST,3);
        put(Direction.SOUTH,4);
        put(Direction.SOUTHWEST,5);
        put(Direction.WEST,6);
        put(Direction.NORTHWEST,7);
    }};

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
        Cache.init(controller);
        Communication.init(controller);
        Pathfinding.init(controller);
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
//                    Util.loop();
                    Cache.loop();
                    bot.turn();
//                    Util.postLoop();
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

}
