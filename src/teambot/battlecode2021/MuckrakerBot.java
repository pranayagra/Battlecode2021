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
    }

    @Override
    public void turn() throws GameActionException {

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
                scoutTarget = Cache.EC_INFO_LOCATION;
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
     * */
    private boolean scoutMovement() throws GameActionException {
        if (!controller.isReady()) {
            return false;
        }

        int moveRes = Pathfinding.move(scoutTarget);
        Debug.printInformation("SCOUT MOVE RESULT", moveRes);

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
