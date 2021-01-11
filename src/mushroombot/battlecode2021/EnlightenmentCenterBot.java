package mushroombot.battlecode2021;

import battlecode.common.*;
import mushroombot.*;
import mushroombot.battlecode2021.util.*;
import mushroombot.battlecode2021.*;

public class EnlightenmentCenterBot implements RunnableBot {
    private RobotController controller;
    public EnlightenmentCenterBot(RobotController controller) throws GameActionException {
        this.controller = controller;
    }

    private int MUCKRAKER_NUM = 0;
    private int SLANDERER_NUM = 0;
    private int POLITICIAN_NUM = 0;
    private int SCOUT_DIRECTION = 0;

    @Override
    public void init() throws GameActionException {

    }

    @Override
    public void turn() throws GameActionException {

        if (!controller.isReady()) {
            return;
        }

        if (tryBuildScout()) {
            return;
        }

        tryBuildArcher();
    }

    public boolean tryBuildScout() throws GameActionException {
        if (MUCKRAKER_NUM < 7) {
            for (Direction dir : RobotPlayer.directions) {
                if (controller.canBuildRobot(RobotType.MUCKRAKER, dir, 1)) {
                    controller.buildRobot(RobotType.MUCKRAKER, dir, 1);
                    Cache.EC_ALL_PRODUCED_ROBOT_IDS.add(controller.senseRobotAtLocation(Cache.CURRENT_LOCATION.add(dir)).ID);
                    Communication.prioritySend(SCOUT_DIRECTION+1,0,0,RobotPlayer.directionIntegerMap.get(dir));
                    SCOUT_DIRECTION ++;
                    MUCKRAKER_NUM ++;
                    return true;
                }
            }
        }
        
        return false;
    }

    public boolean tryBuildArcher() throws GameActionException {
        for (Direction dir : RobotPlayer.directions) {
            if (controller.canBuildRobot(RobotType.MUCKRAKER, dir, 1)) {
                controller.buildRobot(RobotType.MUCKRAKER, dir, 1);
                Cache.EC_ALL_PRODUCED_ROBOT_IDS.add(controller.senseRobotAtLocation(Cache.CURRENT_LOCATION.add(dir)).ID);
                Communication.prioritySend(9,0,0,RobotPlayer.directionIntegerMap.get(dir));
                MUCKRAKER_NUM ++;
                return true;
            }
        }
        
        return false;
    }

}
    

