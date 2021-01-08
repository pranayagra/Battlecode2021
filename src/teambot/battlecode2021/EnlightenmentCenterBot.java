package teambot.battlecode2021;

import battlecode.common.*;
import teambot.*;
import teambot.battlecode2021.util.Debug;

public class EnlightenmentCenterBot implements RunnableBot {
    private RobotController controller;
    public EnlightenmentCenterBot(RobotController controller) throws GameActionException {
        this.controller = controller;
        init();
    }

    @Override
    public void init() throws GameActionException {

    }

    @Override
    public void turn() throws GameActionException {
        if (Debug.debug) {
            System.out.println("I have " + controller.getInfluence() + " " + controller.getConviction());
        }
        for (Direction dir : RobotPlayer.directions) {
            if (controller.canBuildRobot(RobotType.MUCKRAKER, dir, 1)) {
                controller.buildRobot(RobotType.MUCKRAKER, dir, 1);
                break;
            }
        }
    }
}
