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
    private static Map<MapLocation, Constants.FLAG_LOCATION_TYPES> foundECs;

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
        foundECs.put(Cache.myECLocation, Constants.FLAG_LOCATION_TYPES.MY_EC_LOCATION);

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
            controller.move(pathfinding.tryMove(dir));
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

    public boolean attackingPoliticianNearEnemyEC() throws GameActionException {
        boolean runAway = false;

        if (!isBlockingEnemyEC) {
            return runAway;
        }

        MapLocation politicianLocation = null;
        for (RobotInfo robot : Cache.ALL_NEARBY_FRIENDLY_ROBOTS) {
            if (robot.getType() == RobotType.POLITICIAN) {
                if (controller.canGetFlag(robot.ID)) {
                    int flag = controller.getFlag(robot.ID);
                    if (Communication.isPoliticianAttackingFlag(flag)) {
                        politicianLocation = robot.getLocation();
                        runAway = true;
                    }
                }
            }
        }

        //TODO: implement runAway
        if (runAway) {
            int relativeX = Pathfinding.relative(politicianLocation, Cache.CURRENT_LOCATION)[0];
            int relativeY = Pathfinding.relative(politicianLocation, Cache.CURRENT_LOCATION)[1];
            // go opposite direction of politician
        }

        return runAway;
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

        if (random.nextInt(10) > 3) { //60% of the time pathfind
            if (scoutDirection != null) {
                controller.move(pathfinding.tryMove(scoutDirection));

            } else if (scoutTarget != null) {
                controller.move(pathfinding.tryMove(controller.getLocation().directionTo(scoutTarget)));
            }
        } else {
            controller.move(pathfinding.tryMove(pathfinding.COMPASS[random.nextInt(8)]));
        }

        return true;
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
                Constants.FLAG_LOCATION_TYPES locationTypePrevious = foundECs.get(info.location);
                Constants.FLAG_LOCATION_TYPES locationTypeNew = getECType(info.team);

                if (locationTypePrevious == null || locationTypePrevious != locationTypeNew) { //if null or if the type of EC has since changed
                    foundECs.put(info.location, locationTypeNew); //overwrite or add
                    int flag = Communication.encode_LocationType_and_RelativeLocationDataANDHealthData(locationTypeNew, info.location, info.conviction);
                    Debug.printInformation(locationTypeNew + " AT LOCATION " + info.location + " => ", flag);
                    Communication.checkAndAddFlag(flag);
                    //TODO: also communicate EC health next turn... not sure if we can somehow do this in 1 command instead... DONE -> added new flag schema (SCHEMA 3) that uses less precise location for conviction information in 1 turn
                    //I guess we can do in one command if we use at least 9 bits => 6 extraBits + make location less accurate by 4 bits? not sure best way to do this
                    // (might be better to create a diff type of flag SCHEMA instead of changing the current one
                    // We can use relative location SCHEMA instead here ig.... 0b 3 bit schema | 3 location type bits | 8 relative location bits | 10 bits EC amount (value*50 system seems decent?)
                }
            }
        }
    }

    /* Converts the EC Team to a Location Flag Type for communication purposes */
    private Constants.FLAG_LOCATION_TYPES getECType(Team ECTeam) {
        if (ECTeam == Cache.OUR_TEAM) {
            return Constants.FLAG_LOCATION_TYPES.MY_EC_LOCATION;
        } else if (ECTeam == Cache.OPPONENT_TEAM) {
            return Constants.FLAG_LOCATION_TYPES.ENEMY_EC_LOCATION;
        } else {
            return Constants.FLAG_LOCATION_TYPES.NEUTRAL_EC_LOCATION;
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
