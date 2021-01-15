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

        // check if scout

        scoutDirection = Cache.myECLocation.directionTo(controller.getLocation());
        scoutTarget = null;

    }

    @Override
    public void turn() throws GameActionException {
        Debug.printByteCode("");
        if (controller.getInfluence() == 1) scoutRoutine();
        if (controller.getInfluence() > 1) muckWall(Cache.myECID    );
    }

    public boolean scoutRoutine() throws GameActionException {
        if (!controller.isReady()) {
            return false;
        }
        if (random.nextInt(10) > 3) {
            if (scoutDirection != null) {
                controller.move(pathfinding.tryMove(scoutDirection));
                
            } else if (scoutTarget != null) {
                controller.move(pathfinding.tryMove(controller.getLocation().directionTo(scoutTarget)));
            }
        } else {
            controller.move(pathfinding.tryMove(pathfinding.COMPASS[random.nextInt(8)]));
        }
        
        int radius = (int) Math.floor(Math.sqrt(controller.getType().sensorRadiusSquared));
        int x = controller.getLocation().x;
        int y = controller.getLocation().y;

        ArrayList<Direction> testDirections = new ArrayList<>();

        MapLocation testLocation = null;
        for (Direction dir : Pathfinding.COMPASS) {
            switch (dir) {
                case NORTH:
                    testLocation = new MapLocation(x, y + radius);
                    break;
                case SOUTH:
                    testLocation = new MapLocation(x, y - radius);
                    break;
                case EAST:
                    testLocation = new MapLocation(x + radius, y);
                    break;
                case WEST:
                    testLocation = new MapLocation(x - radius, y);
                    break;
            }
            if (testLocation != null && !controller.onTheMap(testLocation)) {
                testDirections.add(dir);
                testLocation = null;
            }
        }

        if (testDirections.size() > 0) {
            Debug.printInformation("edge", testDirections.toString());
        }
        
        boolean exit = false;

        for (int i = 1; i <= radius; i++) {
            for (Direction dir : testDirections) {
                switch (dir) {
                    case NORTH:
                        testLocation = new MapLocation(x, y + i);
                        if (!controller.onTheMap(testLocation)) {
                            reportYEdge(testLocation);
                            exit = true;
                        }
                        break;
                    case SOUTH:
                        testLocation = new MapLocation(x, y - i);
                        if (!controller.onTheMap(testLocation)) {
                            reportYEdge(testLocation);
                            exit = true;
                        }
                        break;
                    case EAST:
                        testLocation = new MapLocation(x + i, y);
                        if (!controller.onTheMap(testLocation)) {
                            reportXEdge(testLocation);
                            exit = true;
                        }
                        break;
                    case WEST:
                        testLocation = new MapLocation(x - i, y);
                        if (!controller.onTheMap(testLocation)) {
                            reportXEdge(testLocation);
                            exit = true;
                        }
                        break;
                }
                if (exit) {
                    scoutDirection = Pathfinding.COMPASS[random.nextInt(8)];
                    break;
                }
            }
        }


        for (RobotInfo info : Cache.ALL_NEARBY_ROBOTS) {
            if (info.ID != Cache.myECID && info.type == RobotType.ENLIGHTENMENT_CENTER) {
                reportEC(info);
            }
        }
        Debug.printByteCode("");
        return true;
    }
    

    public void reportXEdge(MapLocation location) throws GameActionException {
        Communication.checkAndSetFlag(Communication.encode_ExtraANDLocationType_and_ExtraANDLocationData(Constants.FLAG_EXTRA_TYPES.SCOUT, Constants.FLAG_LOCATION_TYPES.TOP_OR_BOTTOM_MAP_LOCATION, 0, location));
    }

    public void reportYEdge(MapLocation location) throws GameActionException {
        Communication.checkAndSetFlag(Communication.encode_ExtraANDLocationType_and_ExtraANDLocationData(Constants.FLAG_EXTRA_TYPES.SCOUT, Constants.FLAG_LOCATION_TYPES.LEFT_OR_RIGHT_MAP_LOCATION, 0, location));
    }

    public void reportEC(RobotInfo info) throws GameActionException {
        if (info.team == controller.getTeam()) {
            Communication.checkAndSetFlag(Communication.encode_ExtraANDLocationType_and_ExtraANDLocationData(Constants.FLAG_EXTRA_TYPES.SCOUT, Constants.FLAG_LOCATION_TYPES.MY_EC_LOCATION, 0, info.location));
        } else if (info.team == Team.NEUTRAL) {
            Communication.checkAndSetFlag(Communication.encode_ExtraANDLocationType_and_ExtraANDLocationData(Constants.FLAG_EXTRA_TYPES.SCOUT, Constants.FLAG_LOCATION_TYPES.NEUTRAL_EC_LOCATION, 0, info.location));
        } else {
            Communication.checkAndSetFlag(Communication.encode_ExtraANDLocationType_and_ExtraANDLocationData(Constants.FLAG_EXTRA_TYPES.SCOUT, Constants.FLAG_LOCATION_TYPES.ENEMY_EC_LOCATION, 0, info.location));
        }
    }

    public void muckWall(int nucleus) throws GameActionException {

        // todo: watch flags for nucleus!

        if (!controller.isReady()) return;
        boolean move = false;
        MapLocation center = null;
        RobotInfo[] nearby = controller.senseNearbyRobots();
        for (RobotInfo info : nearby) {
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
                    System.out.println("e x p o s e d");
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
}
