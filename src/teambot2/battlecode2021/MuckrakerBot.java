package teambot2.battlecode2021;

import battlecode.common.*;
import teambot2.RunnableBot;
import teambot2.battlecode2021.util.*;

import java.util.*;

// Update: See bot "sprint" for legacy code (wall, enemy blocking)

public class MuckrakerBot implements RunnableBot {
    private RobotController controller;
    private Pathfinding pathfinding;
    private static Random random;

    private MapLocation scoutTarget;

    boolean listenToECInstruction;

    private static int numDangerRounds;
    private static boolean protectEC;


    public MuckrakerBot(RobotController controller) throws GameActionException {
        this.controller = controller;
        init();
    }

    @Override
    public void init() throws GameActionException {

        this.pathfinding = new Pathfinding();
        pathfinding.init(controller);

        protectEC = true;

        random = new Random(controller.getID());

        // check if scout
        listenToECInstruction = true;
        
        if (Cache.EC_INFO_ACTION == CommunicationECSpawnFlag.ACTION.SCOUT_LOCATION) {
            scoutTarget = Cache.EC_INFO_LOCATION;
        }
    }

    @Override
    public void turn() throws GameActionException {

        muckrakerComms();

        if (protectEC && ECInDanger()) {
            return;
        }

        numDangerRounds = 0;

        if (simpleAttack()) {
            return;
        }

        switch (Cache.EC_INFO_ACTION) {
            // Attack a known slanderer group
            case ATTACK_LOCATION:
                break;
            case DEFEND_LOCATION:
                //guide strat
                GuideRoutine();
                break;
            case SCOUT_LOCATION:
                scoutRoutine();
                break;
            default:
                break;
        }

        //Debug.printByteCode("turn() => END");
    }

    /* ALL MUCKRAKERS FIRST CHECK IF THE EC IS IN DANGER UPON SPAWN */
    private boolean ECInDanger() throws GameActionException {
        int totalEnemyNearby = 0;
        if (Cache.myECLocation == null) return false;

        for (RobotInfo info : Cache.ALL_NEARBY_ENEMY_ROBOTS) {
            if (info.type == RobotType.POLITICIAN) {
                totalEnemyNearby += Math.max(info.conviction - 10, 0);
            }
        }
        totalEnemyNearby *= controller.getEmpowerFactor(Cache.OPPONENT_TEAM, 0);

        int distance = Cache.CURRENT_LOCATION.distanceSquaredTo(Cache.myECLocation);

        if (numDangerRounds >= 100 && distance == 2) {
            protectEC = false;
        }

        if (controller.canSenseLocation(Cache.myECLocation) && distance <= 2) {
            RobotInfo ECInfo = controller.senseRobotAtLocation(Cache.myECLocation);
            int ECHealth = ECInfo.conviction;
            if (ECHealth <= totalEnemyNearby + 20) {
                if (distance == 2) {
                    for (Direction direction : Constants.CARDINAL_DIRECTIONS) {
                        if (controller.canMove(direction)) {
                            MapLocation candidateLocation = Cache.CURRENT_LOCATION.add(direction);
                            if (candidateLocation.distanceSquaredTo(Cache.myECLocation) == 1) {
                                controller.move(direction);
                            }
                        }
                    }
                }
                ++numDangerRounds;
                return true;
            }
        }
        return false;
    }

    private void muckrakerComms() throws GameActionException {
        // Check EC
        if (controller.canGetFlag(Cache.myECID)) {
            int encoding = controller.getFlag(Cache.myECID);

            processValidFlag(encoding);
        }
        // Check other bots in sight
        for (RobotInfo info : Cache.ALL_NEARBY_FRIENDLY_ROBOTS) {
            if (controller.canGetFlag(info.ID)) {
                int encoding = controller.getFlag(info.ID);
                processValidFlag(encoding);
            }
        }
    }

    private void processValidFlag(int encoding) throws GameActionException {
        if (CommunicationLocation.decodeIsSchemaType(encoding)) {
            CommunicationLocation.FLAG_LOCATION_TYPES locationType = CommunicationLocation.decodeLocationType(encoding);
        MapLocation locationData = CommunicationLocation.decodeLocationData(encoding);
            //Debug.printInformation("Recieving location data", locationData);
            switch (locationType) {
                case NORTH_MAP_LOCATION:
                    Cache.MAP_TOP = locationData.y;
                    break;
                case SOUTH_MAP_LOCATION:
                    Cache.MAP_BOTTOM = locationData.y;
                    break;
                case EAST_MAP_LOCATION:
                    Cache.MAP_RIGHT = locationData.x;
                    break;
                case WEST_MAP_LOCATION:
                    Cache.MAP_LEFT = locationData.x;
                    break;
                case MY_EC_LOCATION: 
                    break;
                case ENEMY_EC_LOCATION:
                    Cache.FOUND_ECS.put(locationData, CommunicationLocation.FLAG_LOCATION_TYPES.ENEMY_EC_LOCATION);
                    Cache.FOUND_ECS_AGE.put(locationData, controller.getRoundNum());
                    // Stream mucks to enemy EC
                    Debug.printInformation("Recieved enemy EC location", locationData);
                    if (Cache.MAP_LEFT > 0 && Cache.MAP_RIGHT > 0 && Cache.MAP_TOP > 0 && Cache.MAP_BOTTOM > 0) {
                        if (Cache.ID % 5 < 4) {
                            scoutTarget = locationData;
                        }
                    }
                    break;
                case NEUTRAL_EC_LOCATION:
                    break;
                default:
                    break;
            }
        }
        // Other flags add here
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
     * Scout:
     *      Perform a movement to optimize scouting of the map
     *      
     *      If discovered all edges of the map
     *      Stochastically select a direction which leads the muckracker to somewhere
     *          1. It has not visited before
     *          2. Away from friendly muckrackers
     *          3. Towards enemy EC
     * */
    public boolean scoutRoutine() throws GameActionException {
        scoutMovement();
        return true;
    }
    
    private boolean scoutMovement() throws GameActionException {
//        controller.setIndicatorLine(Cache.CURRENT_LOCATION, scoutTarget, 255, 0, 0);
        if (!controller.isReady()) {
            return false;
        }

        // Check if target is viable
        boolean switchTarget = false;
        int moveRes = Pathfinding.move(scoutTarget);
        if (moveRes >= 2 || moveRes == 0) {
            switchTarget = true;
        }

        if (Cache.FOUND_ECS.containsKey(scoutTarget) && Cache.FOUND_ECS.get(scoutTarget) == CommunicationLocation.FLAG_LOCATION_TYPES.MY_EC_LOCATION) {
            switchTarget = true;
        }

        if (switchTarget) {
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
                /*
                int[] directionScores = new int[8];
                for (RobotInfo info : Cache.ALL_NEARBY_FRIENDLY_ROBOTS) {
                    if (info.type == RobotType.MUCKRAKER) {
                        Direction dir = Cache.CURRENT_LOCATION.directionTo(info.location);
                    }
                }
                // Unfinished
                */ 
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

    // Because each newly spawned unit (exception slanderer) waits 10 turns
    // They can use 4 turns to get all map locations
    // TODO: Send out closest enemy EC location as well

    private void GuideRoutine() throws GameActionException {
//        controller.setIndicatorDot(Cache.CURRENT_LOCATION, 0,0,255);
        //Debug.printInformation("I'm a guide with", controller.getFlag(controller.getID()));
        // Move away from EC if too close to prevent impact to spawning
        if (Cache.CURRENT_LOCATION.distanceSquaredTo(Cache.myECLocation) <= 2) {
            Pathfinding.naiveMove(Pathfinding.toMovePreferredDirection(Cache.CURRENT_LOCATION.directionTo(Cache.myECLocation).opposite(), 4));
        }
        else if (Cache.CURRENT_LOCATION.distanceSquaredTo(Cache.myECLocation) >= 9) {
            Pathfinding.naiveMove(Cache.myECLocation);
        }

        // Rotate flag to send out map location
        int round = controller.getRoundNum();
        if (Cache.MAP_TOP != 0 && round % 4 == 0) {
            int flag = CommunicationLocation.encodeLOCATION(
                false, true, CommunicationLocation.FLAG_LOCATION_TYPES.NORTH_MAP_LOCATION, 
                new MapLocation(Cache.CURRENT_LOCATION.x,Cache.MAP_TOP));
//            controller.setIndicatorLine(Cache.CURRENT_LOCATION,new MapLocation(Cache.CURRENT_LOCATION.x,Cache.MAP_TOP),255,0,0);
            Comms.scheduleFlag(controller.getRoundNum(),flag);
        }
        else if (Cache.MAP_RIGHT != 0 && round % 4 == 1) {
            int flag = CommunicationLocation.encodeLOCATION(
                false, true, CommunicationLocation.FLAG_LOCATION_TYPES.EAST_MAP_LOCATION, 
                new MapLocation(Cache.MAP_RIGHT,Cache.CURRENT_LOCATION.y));
//            controller.setIndicatorLine(Cache.CURRENT_LOCATION,new MapLocation(Cache.MAP_RIGHT,Cache.CURRENT_LOCATION.y),255,0,0);
            Comms.scheduleFlag(controller.getRoundNum(),flag);
        }
        else if (Cache.MAP_BOTTOM != 0 && round % 4 == 2) {
            int flag = CommunicationLocation.encodeLOCATION(
                false, true, CommunicationLocation.FLAG_LOCATION_TYPES.SOUTH_MAP_LOCATION, 
                new MapLocation(Cache.CURRENT_LOCATION.x,Cache.MAP_BOTTOM));
//            controller.setIndicatorLine(Cache.CURRENT_LOCATION,new MapLocation(Cache.CURRENT_LOCATION.x,Cache.MAP_BOTTOM),255,0,0);
            Comms.scheduleFlag(controller.getRoundNum(),flag);
        }
        else if (Cache.MAP_LEFT != 0 && round % 4 == 3) {
            int flag = CommunicationLocation.encodeLOCATION(
                false, true, CommunicationLocation.FLAG_LOCATION_TYPES.WEST_MAP_LOCATION, 
                new MapLocation(Cache.MAP_LEFT,Cache.CURRENT_LOCATION.y));
//            controller.setIndicatorLine(Cache.CURRENT_LOCATION,new MapLocation(Cache.MAP_LEFT,Cache.CURRENT_LOCATION.y),255,0,0);
            Comms.scheduleFlag(controller.getRoundNum(),flag);
        }
    }
    

}
