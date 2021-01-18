package teambot.battlecode2021;

import battlecode.common.*;
import mushroombot.battlecode2021.util.Communication;
import teambot.RunnableBot;
import teambot.battlecode2021.util.*;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Random;

public class PoliticanBot implements RunnableBot {
    private RobotController controller;
    private static Random random;
    private Pathfinding pathfinding;

    private static int ECFlagForAttackBot;

    private MapLocation[] friendlySlanderers;

    private MapLocation[] muckrakerLocations;
    private int[] muckrakerDistances;
//    private int[] friendlySlandererRobotIDs;

    //TODO: Politican bot upgrade movement (currently bugged and random movement) + fix explosion bug/optimize explosion radius

    public PoliticanBot(RobotController controller) throws GameActionException {
        this.controller = controller;
        init();
    }

    @Override
    public void init() throws GameActionException {
        this.pathfinding = new Pathfinding();
        pathfinding.init(controller);

        random = new Random(controller.getID());

        friendlySlanderers = new MapLocation[80];
        muckrakerLocations = new MapLocation[60];
        muckrakerDistances = new int[60];
//        friendlySlandererRobotIDs = new int[999];

    }

    @Override
    public void turn() throws GameActionException {
        if (Cache.EC_INFO_ACTION == CommunicationECSpawnFlag.ACTION.ATTACK_LOCATION) {
            Debug.printInformation("Moving to destroy netural or enemy EC", "");
            int distance = Cache.CURRENT_LOCATION.distanceSquaredTo(Cache.EC_INFO_LOCATION);
            if (distance <= RobotType.MUCKRAKER.sensorRadiusSquared) {
                attackingPoliticianNearEC();
                Debug.printInformation("SET ATTACKING FLAG", " MOVE ");
            }
            if (moveAndDestroyEC()) return;
        } else {
            if (explodeOnMuckraker()) return;
            if (chaseMuckraker()) return;
            if (leaveLatticeToDefend()) return;
        }

        Debug.printByteCode("END TURN POLI => ");
    }

    //check if any muckraker close enough => explode
    //chaseMuckraker()
    //moveLattice()
    //other stuff()?

    //for all muckrakers in my range, see which ones im closest too when compared to all polis =>
    // then calculate a threat level for all of them =>
    // approach closest such that you are directly in front of it (NWSE, closest to closest slanderer)
    public boolean chaseMuckraker() throws GameActionException {
        int muckrakerSize = 0;

        //quickly find closest one to you
        //get list of muckraker locations + distance to them (travelDistance)
        for (RobotInfo robotInfo : Cache.ALL_NEARBY_ENEMY_ROBOTS) {
            if (robotInfo.type == RobotType.MUCKRAKER) {
                muckrakerLocations[muckrakerSize] = robotInfo.location;
                muckrakerDistances[muckrakerSize++] = Pathfinding.travelDistance(robotInfo.location, Cache.CURRENT_LOCATION);
            }
        }

        if (muckrakerSize == 0) return false;

        //for each politican in range, go through list and prune locations that are further away
        for (RobotInfo robotInfo : Cache.ALL_NEARBY_FRIENDLY_ROBOTS) {
            if (robotInfo.type == RobotType.POLITICIAN) {
                int flag = controller.getFlag(robotInfo.ID);
                if (CommunicationMovement.decodeIsSchemaType(flag) &&
                        CommunicationMovement.decodeMyUnitType(flag) == CommunicationMovement.MY_UNIT_TYPE.SL) continue;

                Debug.printInformation("MUCKRAKER LOCATIONS BEF " + muckrakerSize, Arrays.toString(muckrakerLocations));
                Debug.printInformation("MUCKRAKER DISTANCES BEF " + muckrakerSize, Arrays.toString(muckrakerDistances));

                for (int i = 0; i < muckrakerSize; ++i) {
                    int distance = Pathfinding.travelDistance(robotInfo.location, muckrakerLocations[i]);
                    if (distance < muckrakerDistances[i] || (distance == muckrakerDistances[i] && Cache.ID < robotInfo.ID)) {
                        while (--muckrakerSize >= i + 1) {
                            int newDistance = Pathfinding.travelDistance(robotInfo.location, muckrakerLocations[muckrakerSize]);
                            //If I am closer or if we are the same distance but my ID is higher
                            if (muckrakerDistances[muckrakerSize] < newDistance || (muckrakerDistances[muckrakerSize] == newDistance && Cache.ID > robotInfo.ID)) {
                                muckrakerLocations[i] = muckrakerLocations[muckrakerSize];
                                muckrakerDistances[i] = muckrakerDistances[muckrakerSize];
                                break;
                            }
                        }
                    }
                }

                Debug.printInformation("MUCKRAKER LOCATIONS AFT " + muckrakerSize, Arrays.toString(muckrakerLocations));
                Debug.printInformation("MUCKRAKER DISTANCES AFT " + muckrakerSize, Arrays.toString(muckrakerDistances));

            }
        }

        Debug.printInformation("MUCKRAKER LOCATIONS DONE " + muckrakerSize, Arrays.toString(muckrakerLocations));
        Debug.printInformation("MUCKRAKER DISTANCES DONE " + muckrakerSize, Arrays.toString(muckrakerDistances));

        if (muckrakerSize == 0) return false;

        int target = 0;
        for (int i = 1; i < muckrakerSize; ++i) {
            if (muckrakerDistances[target] > muckrakerDistances[i]) {
                target = i;
            }
        }
        MapLocation targetLocation = muckrakerLocations[target];
        Debug.printInformation("target is " + target + " with location " + targetLocation + " and distance " + muckrakerDistances[target], " TARGET MUCKRAKER");

        if (!controller.isReady()) return false;

        MapLocation bestLocation = null;
        int bestDistance = 99999999;
        for (Direction direction : Constants.CARDINAL_DIRECTIONS) {
            MapLocation candidateLocation = targetLocation.add(direction);
            int dist = candidateLocation.distanceSquaredTo(Cache.myECLocation);
            if (dist < bestDistance) {
                bestDistance = dist;
                bestLocation = candidateLocation;
            }
        }

        if (bestLocation == null) return false;

        Direction toMove = Cache.CURRENT_LOCATION.directionTo(bestLocation);
        Direction validDir = Pathfinding.toMovePreferredDirection(toMove, 1);
        if (validDir != null && toMove != Direction.CENTER) {
            int miniDistance = 999;
            MapLocation expectedLocation = Cache.CURRENT_LOCATION.add(validDir);
            for (RobotInfo robotInfo : Cache.ALL_NEARBY_FRIENDLY_ROBOTS) {
                int encodedFlag = controller.getFlag(robotInfo.ID);
                if (CommunicationMovement.decodeIsSchemaType(encodedFlag)) {
                    CommunicationMovement.MY_UNIT_TYPE myUnitType = CommunicationMovement.decodeMyUnitType(encodedFlag);
                    if (myUnitType == CommunicationMovement.MY_UNIT_TYPE.SL) {
                        int distance = Pathfinding.travelDistance(expectedLocation, robotInfo.location);
                        miniDistance = Math.min(miniDistance, distance);
                    }
                }
            }
            if (miniDistance <= 4) {
                controller.move(validDir);
                Debug.printInformation("MOVING FOR MUCKRAKER ", validDir);
            }
        }

        return false;
    }

    //TODO: also move away from other politicians?
    public boolean leaveLatticeToDefend() throws GameActionException {
//        boolean closeToSlanderer = false;
        int miniDistance = 999;

        if (!controller.isReady()) return false;

        for (RobotInfo robotInfo : Cache.ALL_NEARBY_FRIENDLY_ROBOTS) {
            if (controller.canGetFlag(robotInfo.ID)) {
                int encodedFlag = controller.getFlag(robotInfo.ID);
                if (CommunicationMovement.decodeIsSchemaType(encodedFlag)) {
                    CommunicationMovement.MY_UNIT_TYPE myUnitType = CommunicationMovement.decodeMyUnitType(encodedFlag);
                    int distance = Pathfinding.travelDistance(Cache.CURRENT_LOCATION, robotInfo.location);
                    if (myUnitType == CommunicationMovement.MY_UNIT_TYPE.SL) {
                        miniDistance = Math.min(miniDistance, distance);
                        if (distance <= 2) {
                            Direction preferredDirection = Cache.myECLocation.directionTo(Cache.CURRENT_LOCATION);
                            Direction validDirection = Pathfinding.toMovePreferredDirection(preferredDirection, 2);
                            if (validDirection != null) {
                                controller.move(validDirection);
                                return true;
                            }
                        }
                    }
                }
            }
        }

        // TOO FAR FROM CLOSEST SLANDERER, GO CLOSER
        if (miniDistance >= 3) {
            Direction preferredDirection = Cache.CURRENT_LOCATION.directionTo(Cache.myECLocation);
            Direction validDirection = Pathfinding.toMovePreferredDirection(preferredDirection, 2);
            if (validDirection != null) {
                controller.move(validDirection);
                return true;
            }
        }
        return false;
    }

    //Assumptions: neutralEC is found by a scout and communicated to the EC
    //Bugs:
    //  friendly units nearby our base may move around as a result of the flag. We should have a "no matter what I will not move" flag to avoid this danger situation (especially with wall units) and only set the flag when once
    //  What if two ECs close together? Then nearbyRobots.length == 1 is always false (I think this condition is too risky regardless of this situation)

    //TODO: I think protocol should be ->
    // EC spawns politician (maybe let's say xx2 influence politicians are type neutral EC explode) and let's this bot know the location of the neutralEC
    // Then this bot pathfinds to the location, waits until it is somewhat close, sets its flag to attacking, and then explodes based on condition (hard part)

    // Enhancements:
    //      the "actionRadius" is not always the most preferred explosion radius. Rather, smaller is usually better here
    //      the nearbyRobots.length == 1 is too strict (we cannot force enemy robots to leave). Rather, the politican should be able to the best location
    //      we only explode if it is a 1-shot (with some extra for health). If it is no longer a 1-shot, this politican is repurposed.
    //      check for enemy politicians?
    //      We may want a way to clear enemies that just surround the neutral EC with weak targets but do not capture
    //TODO: still try to optimize the location spawned (get as close as possible and then explode)
    public boolean moveAndDestroyEC() throws GameActionException {

        int actionRadius = Cache.ROBOT_TYPE.actionRadiusSquared;
        boolean ECExists = false;
        int distance = Cache.CURRENT_LOCATION.distanceSquaredTo(Cache.EC_INFO_LOCATION);
        if (distance > actionRadius) {
            pathfinding.move(Cache.EC_INFO_LOCATION);
            return true;
        }

        RobotInfo[] nearbyRobots = controller.senseNearbyRobots(actionRadius);
        for (RobotInfo robotInfo : nearbyRobots) {
            if (robotInfo.type == RobotType.ENLIGHTENMENT_CENTER && (robotInfo.team == Team.NEUTRAL || robotInfo.team == Cache.OPPONENT_TEAM)) {
                // explode if no other nearby allied muckrakers or slanderers
                ECExists = true;
                int squaredDistance = controller.getLocation().distanceSquaredTo(robotInfo.getLocation());
                RobotInfo[] nearbyAlliedRobots = controller.senseNearbyRobots(squaredDistance, controller.getTeam());
                boolean safeToEmpower = true;
                for (RobotInfo nearbyAlliedRobot : nearbyAlliedRobots) {
                    if (nearbyAlliedRobot.type == RobotType.MUCKRAKER || nearbyAlliedRobot.type == RobotType.SLANDERER) {
                        safeToEmpower = false;
                    }
                }
                if (safeToEmpower && controller.canEmpower(actionRadius)) {
                    controller.empower(squaredDistance);
                    return true;
                } else if (!safeToEmpower) {
                    int flag = CommunicationMovement.encodeMovement(true, true,
                            CommunicationMovement.MY_UNIT_TYPE.PO, CommunicationMovement.MOVEMENT_BOTS_DATA.NOOP,
                            CommunicationMovement.COMMUNICATION_TO_OTHER_BOTS.NOOP, false, false, 0);
                    if (!Comms.hasSetFlag && controller.canSetFlag(flag)) {
                        controller.setFlag(flag);
                        Comms.hasSetFlag = true;
                    }
                }
            }
        }

        if (!ECExists) {
//            Cache.EC_INFO_ACTION = CommunicationECSpawnFlag.ACTION.DEFEND_LOCATION;
        }

        return true;
    }

    //chasing flag -> flagType | robotID
    // has some bugs! Pranay's Method...
    // enhancements:
    //      stick to a certain distance away from slanderers or better yet muckrakers (outside the wall) instead of random movement
    //      only focus on enemy politicians maybe? since muckrakers can't get through the wall anyways... risky
    //      explosion radius
    //      sometimes we move which causes us to not be able to cast ability until later
    //      there are some bugs with not exploding at the right time, or multiple bots exloding at the same time
    //
    public boolean explodeOnMuckraker() throws GameActionException {

        int friendlySlanderersSize = 0;

        for (RobotInfo robotInfo : Cache.ALL_NEARBY_FRIENDLY_ROBOTS) {
            if (robotInfo.type == RobotType.POLITICIAN) {
                if (controller.canGetFlag(robotInfo.ID)) {
                    int encodedFlag = controller.getFlag(robotInfo.ID);
                    if (CommunicationMovement.decodeIsSchemaType(encodedFlag) && CommunicationMovement.decodeMyUnitType(encodedFlag) == CommunicationMovement.MY_UNIT_TYPE.SL) {
                        // This is a slanderer on our team...
                        friendlySlanderers[friendlySlanderersSize++] = robotInfo.location;
                    }
                }
            }
        }


        Debug.printInformation("friendlySlanderers Size ", friendlySlanderersSize);
        Debug.printInformation("friendlySlanderers ", Arrays.toString(friendlySlanderers));

        MapLocation toTarget = null;
        int leastDistance = 9999;
        boolean enemyMuckrakerInRange = false;
        for (RobotInfo robotInfo : controller.senseNearbyRobots(-1, Cache.OPPONENT_TEAM)) {
            if (robotInfo.type == RobotType.MUCKRAKER) {
                enemyMuckrakerInRange = true;
                int minDistance = 9998;
                //enemy muckraker
                //TODO: make sure that no friendly unit is already tracking the muckraker -- maybe don't implement this, not high reward and risky..
                //TODO: leave the muckraker alone if it goes far enough from our defense location
                if (Debug.debug) System.out.println("found enemy MUCKRAKER at " + robotInfo.location);
                //find minimum distance from slanderers to enemy
                for (int i = 0; i < friendlySlanderersSize; ++i) {
                    minDistance = Math.min(minDistance, robotInfo.location.distanceSquaredTo(friendlySlanderers[i]));
                }
                if (leastDistance > minDistance) {
                    leastDistance = minDistance;
                    toTarget = robotInfo.location;
                }
            }
        }

        int flag = CommunicationMovement.encodeMovement(true, true,
                CommunicationMovement.MY_UNIT_TYPE.PO, CommunicationMovement.MOVEMENT_BOTS_DATA.NOOP,
                CommunicationMovement.COMMUNICATION_TO_OTHER_BOTS.NOOP, false, false, 0);

        if (enemyMuckrakerInRange && friendlySlanderersSize > 0) { //If I have both a muckraker and slanderer in range of me, alert danger by inDanger to slanderers!
            Direction dangerDirection = Cache.myECLocation.directionTo(toTarget);
            flag = CommunicationMovement.encodeMovement(true, true,
                    CommunicationMovement.MY_UNIT_TYPE.PO, CommunicationMovement.convert_DirectionInt_MovementBotsData(dangerDirection.ordinal()),
                    CommunicationMovement.COMMUNICATION_TO_OTHER_BOTS.NOOP, false, true, 0);
            if (!Comms.hasSetFlag && controller.canSetFlag(flag)) {
                controller.setFlag(flag);
                Comms.hasSetFlag = true;
            }
        } else {
            if (!Comms.hasSetFlag && controller.canSetFlag(flag)) {
                controller.setFlag(flag);
                Comms.hasSetFlag = false; // NOTE: FALSE HERE IS BY FUNCTION (not a bug). We want to change the flag only to change its state from the previous one. If it is set to a different state/overridden, that is OKAY
            }
        }

        Debug.printInformation("Minimum distance enemy muckraker is to slanderer is ", leastDistance);
        Debug.printInformation("and targeting location ", toTarget);

        if (controller.isReady() && leastDistance <= RobotType.MUCKRAKER.actionRadiusSquared + 5) {
            if (controller.canEmpower(Cache.CURRENT_LOCATION.distanceSquaredTo(toTarget))) {
                controller.empower(Cache.CURRENT_LOCATION.distanceSquaredTo(toTarget));
                return true;
            }
        }
        return false;
    }

    //ASSUME POLITICIAN CAN PATHFIND TO EC LOCATION

    public boolean attackingPoliticianNearEC() throws GameActionException {
        int flag = CommunicationMovement.encodeMovement(
                true,true, CommunicationMovement.MY_UNIT_TYPE.PO, CommunicationMovement.MOVEMENT_BOTS_DATA.IN_DANGER_MOVE,
                CommunicationMovement.COMMUNICATION_TO_OTHER_BOTS.MOVE_AWAY_FROM_ME, false,false,0);
        if (!Comms.hasSetFlag && controller.canSetFlag(flag)) {
            controller.setFlag(flag); //CARE ABOUT SEED LATER? //NOTE THIS IS SETFLAG BECAUSE WE DO NOT WANT TO QUEUE IT BUT SKIP QUEUE AND SET FLAG
            Comms.hasSetFlag = true;
            return true;
        }
        return false;
    }




}
