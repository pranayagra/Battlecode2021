package teambot.battlecode2021;

import battlecode.common.*;
import teambot.*;
import teambot.battlecode2021.util.Pathfinding;

public class SlandererBot implements RunnableBot {
    private RobotController controller;
    private Pathfinding pathfinding;
    public SlandererBot(RobotController controller) throws GameActionException {
        this.controller = controller;
        init();
    }

    @Override
    public void init() throws GameActionException {
        this.pathfinding = new Pathfinding();
        pathfinding.init(controller);
    }

    @Override
    public void turn() throws GameActionException {
    }
}
