package teambot.battlecode2021;

import battlecode.common.*;
import teambot.RunnableBot;
import teambot.battlecode2021.util.*;

import java.nio.file.Path;
import java.util.*;

public class MuckrakerBot implements RunnableBot {
    private RobotController controller;
    private Pathfinding pathfinding;
    private static Random random;

    private MapLocation scoutTarget;

    boolean listenToECInstruction;


    public MuckrakerBot(RobotController controller) throws GameActionException {
        this.controller = controller;
        init();
    }

    @Override
    public void init() throws GameActionException {

        this.pathfinding = new Pathfinding();
        pathfinding.init(controller);

        random = new Random(controller.getID());

        Cache.FOUND_ECS.put(Cache.myECLocation, CommunicationLocation.FLAG_LOCATION_TYPES.MY_EC_LOCATION);

        // check if scout
        listenToECInstruction = true;
        
        if (Cache.EC_INFO_ACTION == CommunicationECSpawnFlag.ACTION.SCOUT_LOCATION) {
            scoutTarget = Cache.EC_INFO_LOCATION;
        }
    }

    @Override
    public void turn() throws GameActionException {

        if (simpleAttack()) {
            return;
        }

        switch (Cache.EC_INFO_ACTION) {
            case ATTACK_LOCATION:
                break;
            case DEFEND_LOCATION:
                muckWall(Cache.myECID);
                //TODO: close wall if and only if we can sense an enemy (move a rank up or down, not sure which)
                //TODO: create a structure around slanderers, not EC
                //TODO: some type of communication between EC spawn location (or flag) and direction (or location) to fill wall, which is intitated by slanderers?
                break;
            case SCOUT_LOCATION:
                scoutRoutine();
                break;
        }

        Debug.printByteCode("turn() => END");
    }

    public boolean scoutRoutine() throws GameActionException {
        scoutMovement();
        return true;
    }

    // If you can still sense the nucleus, then move away from it greedily
    /* brainstorming ideas ->
    *       1) we should do something based on slanderers (not EC)
    *       2) we want lattice structure with rectangle
    * */
    // Bug: tryMove() may return invalid direction
    public void muckWall(int nucleus) throws GameActionException {

        // TODO: watch flags for nucleus!

        if (!controller.isReady()) return;
        // TODO: Do pathfinding when not ready

        if (simpleAttack()) {
            return;
        }

        boolean move = false;
        MapLocation center = null;
        for (RobotInfo info : Cache.ALL_NEARBY_ROBOTS) {
            if (info.ID == nucleus) {
                move = true;
                center = info.location;
                break;
            }
        }
        if (move) {
            Direction dir = center.directionTo(controller.getLocation());
            Direction moveDirection = pathfinding.toMovePreferredDirection(dir, 4);
            if (moveDirection != null) controller.move(moveDirection);
        }
    }

    //assume robot always tries to surround/get as close as possible to a EC (only 1 distance, extras can roam around to edge map/bounce around)

    public boolean updateBlockingEnemyEC() {
        for (RobotInfo robot : Cache.ALL_NEARBY_ENEMY_ROBOTS) {
            if (robot.getType() == RobotType.ENLIGHTENMENT_CENTER) {
                return true;
            }
        }
        return false;
    }

    public boolean simpleAttack() throws GameActionException {
        Team enemy = Cache.OPPONENT_TEAM;
        int actionRadius = controller.getType().actionRadiusSquared;

        for (RobotInfo robot : controller.senseNearbyRobots(actionRadius, enemy)) {
            if (robot.type.canBeExposed()) {
                // It's a slanderer... go get them!
                if (controller.canExpose(robot.location)) {
                    controller.expose(robot.location);
                    return true;
                }
            }
        }

        for (RobotInfo robot : Cache.ALL_NEARBY_ENEMY_ROBOTS) {
            if (robot.type.canBeExposed()) {
                Pathfinding.move(robot.location);
                return true;
            }
        }
        return false;
    }

    /*
     * Functionality:
     *      Perform a movement to optimize scouting of the map
     *      
     *      If discovered all edges of the map
     *      Stochastically select a direction which leads the muckracker to somewhere
     *          1. It has not visited before
     *          2. Away from friendly muckrackers
     *          3. Towards enemy EC
     * */
    private boolean scoutMovement() throws GameActionException {
        if (!controller.isReady()) {
            return false;
        }

        Direction moveDirection = null;
        int moveRes = Pathfinding.move(scoutTarget);
        if (moveRes == 2 || scoutTarget.equals(Cache.CURRENT_LOCATION)) {
            Cache.CURRENT_LOCATION = controller.getLocation();
            //TODO: Adjust so muckracker chooses a side thats likely to be close
            if (Cache.MAP_TOP == 0) {
                scoutTarget = Cache.CURRENT_LOCATION.translate(0,64);
            }
            else if (Cache.MAP_RIGHT == 0) {
                scoutTarget = Cache.CURRENT_LOCATION.translate(64,0);
            }
            else if (Cache.MAP_BOTTOM == 0) {
                scoutTarget = Cache.CURRENT_LOCATION.translate(0,-64);
            }
            else if (Cache.MAP_LEFT == 0) {
                scoutTarget = Cache.CURRENT_LOCATION.translate(-64,0);
            }
            else{
                int[] directionScores = new int[8];
                for (RobotInfo info : Cache.ALL_NEARBY_FRIENDLY_ROBOTS) {
                    if (info.type == RobotType.MUCKRAKER) {
                        Direction dir = Cache.CURRENT_LOCATION.directionTo(info.location);
                    }
                }
                // Unfinished
                scoutTarget = Pathfinding.randomLocation();
            }
        }

        return false;
    }

    /* 
    * Guide 
    * Moves to location not adjacent to friendly EC
    * And displays information
    */


    /*
    private void GuideRoutine() {
        if (Pathfinding.distan)
    }
    */

}
