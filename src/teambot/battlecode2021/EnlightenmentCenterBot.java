package teambot.battlecode2021;

import battlecode.common.*;
import teambot.*;

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
        for (Direction dir : RobotPlayer.directions) {
            if (controller.canBuildRobot(RobotType.MUCKRAKER, dir, 1)) {
                controller.buildRobot(RobotType.MUCKRAKER, dir, 1);
                break;
            }
        }
    }
}
