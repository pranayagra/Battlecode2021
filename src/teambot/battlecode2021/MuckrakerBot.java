package teambot.battlecode2021;

import battlecode.common.*;
import teambot.RunnableBot;
import teambot.battlecode2021.util.*;

import java.util.*;

public class MuckrakerBot implements RunnableBot {
    private RobotController controller;
    private Pathfinding pathfinding;
    private static Random random;

    private MapLocation scoutTarget;
    private Direction scoutDirection;

    int relativeX;
    int relativeY;
    private static boolean isBlockingEnemyEC;

    //Behavior: if does not exist, add to map. If exists, check type (neutral, friendly, enemy) and see if it has changed
    private static Map<MapLocation, CommunicationLocation.FLAG_LOCATION_TYPES> foundECs;

    private static boolean updatedFlagThisRound;


    public MuckrakerBot(RobotController controller) throws GameActionException {
        this.controller = controller;
        init();
    }

    @Override
    public void init() throws GameActionException {

        this.pathfinding = new Pathfinding();
        pathfinding.init(controller);

        random = new Random(controller.getID());
        isBlockingEnemyEC = false;

        foundECs = new HashMap();
        foundECs.put(Cache.myECLocation, CommunicationLocation.FLAG_LOCATION_TYPES.MY_EC_LOCATION);

        // check if scout

        scoutDirection = Cache.myECLocation.directionTo(Cache.CURRENT_LOCATION);
        scoutTarget = null;

    }

    @Override
    public void turn() throws GameActionException {
        updatedFlagThisRound = false;

        int type = controller.getInfluence();
        switch (type) {
            case 1:
                scoutRoutine();
                break;
            case 2:
                muckWall(Cache.myECID);
                //TODO: close wall if and only if we can sense an enemy (move a rank up or down, not sure which)
                //TODO: create a structure around slanderers, not EC
                //TODO: some type of communication between EC spawn location (or flag) and direction (or location) to fill wall, which is intitated by slanderers?
                break;
            case 3:

            default:
                break;
        }

        Debug.printByteCode("turn() => END");
    }

    public boolean scoutRoutine() throws GameActionException {

        scoutMovement();

        /* REMEMBER TO BE CAREFUL USING LOCATION/ENVIRONMENT CACHE VARIABLES SINCE WE MOVE FIRST! UPDATE FIRST */
        Cache.CURRENT_LOCATION = controller.getLocation();
        Cache.ALL_NEARBY_ROBOTS = controller.senseNearbyRobots();

        scoutECs();
        
        Debug.printByteCode("scoutRoutine() => END");
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
     * Bugs:
     *      I think this code is not safe (pathfinding.tryMove() can return an invalid direction which will cause an exception to controller.move())
     * Enhancements:
     *      Find a better way to scout the map (can possibly set target locations based on spawn or something)
     *
     * */
    private boolean scoutMovement() throws GameActionException {
        if (!controller.isReady()) {
            return false;
        }

        Direction moveDirection = null;

        if (random.nextInt(10) > 3) { //60% of the time pathfind
            if (scoutDirection != null) {
                moveDirection = pathfinding.toMovePreferredDirection(scoutDirection, 4);

            } else if (scoutTarget != null) {
                moveDirection = pathfinding.toMovePreferredDirection(Cache.CURRENT_LOCATION.directionTo(scoutTarget), 4);

            }
        } else {
            moveDirection = pathfinding.randomValidDirection();
        }

        if (moveDirection != null) {
            controller.move(moveDirection);
            return true;
        }

        return false;
    }

    /* Find all ECs in sensor range and communicate it if and only if
    *  1) the bot has never seen it before OR
    *  2) the bot previously scouted it but has since changed teams (ally, enemy, neutral)
    *
    * Changelog: removed avoiding the current EC because we could lose it mid-game, but it is not added to the communicationQueue since we prepopulate the map
    * */
    private void scoutECs() throws GameActionException {
        for (RobotInfo info : Cache.ALL_NEARBY_ROBOTS) { //incorrect use of cache due to location - reminder update
            if (info.type == RobotType.ENLIGHTENMENT_CENTER) {
                Debug.printInformation( "checking EC location " + info.location + " => ", "");
                CommunicationLocation.FLAG_LOCATION_TYPES locationTypePrevious = foundECs.get(info.location);
                CommunicationLocation.FLAG_LOCATION_TYPES locationTypeNew = getECType(info.team);

                if (locationTypePrevious == null || locationTypePrevious != locationTypeNew) { //if null or if the type of EC has since changed
                    foundECs.put(info.location, locationTypeNew); //overwrite or add
                    int flag = CommunicationLocation.encodeLOCATION(false, false, locationTypeNew, info.location);
                    Comms.checkAndAddFlag(flag);
                    flag = CommunicationECInfo.encodeECInfo(false, false, getCommunicatedUnitTeamForECInfo(info.team), info.conviction);
                    Comms.checkAndAddFlag(flag);
                    flag = CommunicationRobotID.encodeRobotID(false,true, CommunicationRobotID.COMMUNICATION_UNIT_TYPE.EC, getCommunicatedUnitTeamForRobotID(info.team), info.ID);
                    Comms.checkAndAddFlag(flag);
                }
            }
        }
    }

    /* Converts the EC Team to a Location Flag Type for communication purposes */
    private CommunicationLocation.FLAG_LOCATION_TYPES getECType(Team ECTeam) {
        if (ECTeam.equals(Cache.OUR_TEAM)) {
            return CommunicationLocation.FLAG_LOCATION_TYPES.MY_EC_LOCATION;
        } else if (ECTeam.equals(Cache.OPPONENT_TEAM)) {
            return CommunicationLocation.FLAG_LOCATION_TYPES.ENEMY_EC_LOCATION;
        } else {
            return CommunicationLocation.FLAG_LOCATION_TYPES.NEUTRAL_EC_LOCATION;
        }
    }

    private CommunicationECInfo.COMMUNICATION_UNIT_TEAM getCommunicatedUnitTeamForECInfo(Team ECTeam) {
        if (ECTeam.equals(Cache.OUR_TEAM)) {
            return CommunicationECInfo.COMMUNICATION_UNIT_TEAM.MY;
        } else if (ECTeam.equals(Cache.OPPONENT_TEAM)) {
            return CommunicationECInfo.COMMUNICATION_UNIT_TEAM.ENEMY;
        } else {
            return CommunicationECInfo.COMMUNICATION_UNIT_TEAM.NEUTRAL;
        }
    }

    private CommunicationRobotID.COMMUNICATION_UNIT_TEAM getCommunicatedUnitTeamForRobotID(Team ECTeam) {
        if (ECTeam.equals(Cache.OUR_TEAM)) {
            return CommunicationRobotID.COMMUNICATION_UNIT_TEAM.MY;
        } else if (ECTeam.equals(Cache.OPPONENT_TEAM)) {
            return CommunicationRobotID.COMMUNICATION_UNIT_TEAM.ENEMY;
        } else {
            return CommunicationRobotID.COMMUNICATION_UNIT_TEAM.NEUTRAL;
        }
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
