package teambot.battlecode2021;

import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import teambot.RunnableBot;
import teambot.battlecode2021.util.Cache;

import java.util.Random;

public class MuckrakerBot implements RunnableBot {
    private RobotController controller;
    private Random;
    private MapLocation target;

    public MuckrakerBot(RobotController controller) {
        this.controller = controller;
    }

    @Override
    public void init() throws GameActionException {

//        target = Cache.CURRENT_LOCATION.x
    }

    @Override
    public void turn() throws GameActionException {

    }
}
