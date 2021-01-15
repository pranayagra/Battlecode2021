package teambot.battlecode2021;

import battlecode.common.*;
import com.sun.tools.internal.jxc.ap.Const;
import teambot.*;
import teambot.battlecode2021.util.*;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;

public class SlandererBot implements RunnableBot {
    private RobotController controller;
    private Pathfinding pathfinding;

    private int[] canMoveIndices;
    private MapLocation[] moveLocs;
    private double[] moveRewards;
    private int distanceFromMyEC;

    //TODO: CHECK AND SET FLAG IF WALL IS MISSING AROUND US? and probably set flag based on it

    public SlandererBot(RobotController controller) throws GameActionException {
        this.controller = controller;
        init();
    }

    @Override
    public void init() throws GameActionException {
        this.pathfinding = new Pathfinding();
        pathfinding.init(controller);
        distanceFromMyEC = 1;
        moveLocs = new MapLocation[9];
        moveRewards = new double[9];
        canMoveIndices = new int[9];
    }

    @Override
    public void turn() throws GameActionException {
        spawnInLattice();
        Debug.printByteCode("after lattice ");
    }

    /* return: abs(deltaX) + abs(deltaY) between "location" and the starting EC location */
    private int calculateLocationDistanceFromMyEC(MapLocation location) {
        return Math.abs(location.x - Cache.myECLocation.x) + Math.abs(location.y - Cache.myECLocation.y);
    }

    /* Behavior =>
        Good Square => not blocking the EC AND an odd distance away
        Bad Square => blocking the EC or an even distance away
    Return: true if and only if the square is good
    */
    private boolean checkIfGoodSquare(MapLocation location) {
        int distance = calculateLocationDistanceFromMyEC(location);
        if (distance <= 2 || distance % 2 == 0) {
            return false;
        }
        return true;
    }


    /* Create lattice structure of slanderers centered around the EC location
    * Potential Bugs:
    *       if two seperate ECs collide slanderers with each other
    *       if one side of the EC is overproduced and bots can't get further away... is this really a bug tho or a feature?
    * */
    public void spawnInLattice() throws GameActionException {
        distanceFromMyEC = calculateLocationDistanceFromMyEC(Cache.CURRENT_LOCATION);
        boolean isMyCurrentSquareGood = checkIfGoodSquare(Cache.CURRENT_LOCATION);

        // if in danger from muckraker, get out
        if (runFromMuckrakerMove() != 0) {
            return;
        }

        if (isMyCurrentSquareGood) {
            currentSquareIsGoodExecute();
        } else {
            currentSquareIsBadExecute();
        }

    }

    public void unitInDanger() throws GameActionException {
        if (!controller.isReady()) return;

        int minimizedDistance = distanceFromMyEC;
        Direction minimizedDirection = null;

        for (Direction direction : Constants.DIRECTIONS) {
            if (controller.canMove(direction)) {
                MapLocation candidateLocation = Cache.CURRENT_LOCATION.add(direction);
                int candidateDistance = calculateLocationDistanceFromMyEC(candidateLocation);
            }
        }

        Pathfinding.move(Cache.myECLocation);
    }

    /* Execute behavior if current square is a "bad" square
     * Behavior: perform a moving action to square in the following priority ->
     *          If there exists a good square that the bot can move to regardless of distance, then move to the one that is closest to the EC
     *          If there exists a bad square that the bot can move to that is further from the EC than the current square, then move to the one that is furthest to the EC
     *          Else => do nothing
     * */
    public void currentSquareIsBadExecute() throws GameActionException {

        if (!controller.isReady()) return;

        int badSquareMaximizedDistance = distanceFromMyEC;
        Direction badSquareMaximizedDirection = null;

        // try to find a good square

        // move further or equal to EC

        int goodSquareMinimizedDistance = (int) 1E9;
        Direction goodSquareMinimizedDirection = null;

        for (Direction direction : Constants.DIRECTIONS) {
            if (controller.canMove(direction)) {
                MapLocation candidateLocation = Cache.CURRENT_LOCATION.add(direction);
                int candidateDistance = calculateLocationDistanceFromMyEC(candidateLocation);
                boolean isGoodSquare = checkIfGoodSquare(candidateLocation);
                if (isGoodSquare) {
                    if (goodSquareMinimizedDistance > candidateDistance) {
                        goodSquareMinimizedDistance = candidateDistance;
                        goodSquareMinimizedDirection = direction;
                    }
                } else {
                    if (badSquareMaximizedDistance <= candidateDistance) {
                        badSquareMaximizedDistance = candidateDistance;
                        badSquareMaximizedDirection = direction;
                    }
                }
            }
        }

        if (goodSquareMinimizedDirection != null) {
            controller.move(goodSquareMinimizedDirection);
        } else if (badSquareMaximizedDirection != null) {
            controller.move(badSquareMaximizedDirection);
        } else {
            // stuck, forfeit turn
        }

    }

    /* Execute behavior if current square is a "good" square
    * Behavior:
    *           perform a moving action to square if and only if the square is a good square AND it is closer to the EC AND if we are ready
    *           else: do nothing
    * */
    public void currentSquareIsGoodExecute() throws GameActionException {
        // try to move towards EC with any ordinal directions that decreases distance (NE, SE, SW, NW)

        if (!controller.isReady()) return;

        int moveTowardsDistance = distanceFromMyEC;
        Direction moveTowardsDirection = null;

        for (Direction direction : Constants.DIRECTIONS) {
            if (controller.canMove(direction)) {
                MapLocation candidateLocation = Cache.CURRENT_LOCATION.add(direction);
                int candidateDistance = calculateLocationDistanceFromMyEC(candidateLocation);
                boolean isGoodSquare = checkIfGoodSquare(candidateLocation);
                if (isGoodSquare && candidateDistance < moveTowardsDistance) {
                    moveTowardsDistance = candidateDistance;
                    moveTowardsDirection = direction;
                }
            }
        }

        if (moveTowardsDirection != null) {
            controller.move(moveTowardsDirection);
        }
    }


    /* Check and then execute behavior if the bot needs to run away from Muckrakers
    Algorithm:
        1) For all 9 action decisions (no action + move in 8 directions), calculate the location that is furthest from all enemy muckrakers. Call this moveRewards[] + rewardOfStaying
        2) Set flag to the best reward of the 9 action decisions regardless of feasibility
        3) Select one of 9 decisions among the valid ones via controller.canMove(direction) (including no action)
        4) Terminate algorithm UNLESS no muckrakers were found (meaning the rewards were useless)
        5) Iterate through all friendly robots of type slanderers and find the closest one with a movement flag with danger bit. Call this preferedMovementDirectionIdx
        6) Attempt to move in preferedMovementDirectionIdx direction. If cannot move, try the next-to directions
        7) else: return 0
    Set: flag to danger if muckraker is within sensor range

    Return: 0 means we are not moving because there is no reason to move,
            1 means we are not moving because we shouldn't move (or can't)
            2 means we are moving to a safer location
     */
    public int runFromMuckrakerMove() throws GameActionException {

        //flag indicates best direction to move, not direction I am moving...
//        Debug.printByteCode("runFromMuckrakerMove() => before run " + controller.getCooldownTurns());

        boolean foundEnemyMuckraker = false;
        double rewardOfStaying = 9999; //if valStaying > max(dirs), don't move

        int canMoveIndicesSize = 0;
        int idx = 0;
        for (Direction dir : RobotPlayer.directions) {
            moveRewards[idx] = 9998;
            moveLocs[idx] = Cache.CURRENT_LOCATION.add(dir);
            if (controller.canMove(dir)) {
                canMoveIndices[canMoveIndicesSize++] = idx;
            }
            ++idx;
        }

//        Debug.printByteCode("runFromMuckrakerMove() => computed all valid dirs + locations ");
//        Debug.printInformation("valid directions is sized " + canMoveIndicesSize + " and has ", Arrays.toString(canMoveIndices));

        for (RobotInfo robotInfo : Cache.ALL_NEARBY_ENEMY_ROBOTS) {
            if (robotInfo.getType() == RobotType.MUCKRAKER) {
                foundEnemyMuckraker = true;
                MapLocation enemyLocation = robotInfo.location;
                //for all valid locations, find travelDistance...
                rewardOfStaying = Math.min(rewardOfStaying, Pathfinding.travelDistance(Cache.CURRENT_LOCATION, enemyLocation) + 0.01 * Cache.CURRENT_LOCATION.distanceSquaredTo(enemyLocation));
                for (int i = 0; i < idx; ++i) {
                    moveRewards[i] = Math.min(moveRewards[i], Pathfinding.travelDistance(moveLocs[i], enemyLocation) + 0.01 * moveLocs[i].distanceSquaredTo(enemyLocation));
                }
            }
        }


//        Debug.printInformation("my location reward is ", rewardOfStaying);
//        Debug.printInformation("location rewards surrounding me is ", Arrays.toString(moveRewards));
//        Debug.printByteCode("runFromMuckrakerMove() => SCANNED ENEMY LOCATIONS ");
        int flag = Communication.encode_MovementBotType_and_MovementBotData
                (Constants.MOVEMENT_BOTS_TYPES.SLANDERER_TYPE, Constants.MOVEMENT_BOTS_DATA.NOT_MOVING, false);
        int bestValidDirection = -1;
        double bestValidReward = rewardOfStaying;

        if (foundEnemyMuckraker) {
            int bestDirection = -1;
            double bestReward = rewardOfStaying;

            for (int i = 0; i < idx; ++i) {
                if (moveRewards[i] > bestReward) { //find the best direction based on the reward
                    bestDirection = i;
                    bestReward = moveRewards[i];
                }
            }

            flag = Communication.encode_MovementBotType_and_MovementBotData(Constants.MOVEMENT_BOTS_TYPES.SLANDERER_TYPE, Communication.convert_DirectionInt_MovementBotsData(bestDirection), true);


            for (int i = 0; i < canMoveIndicesSize; ++i) {
                if (moveRewards[canMoveIndices[i]] > bestValidReward) {
//                    Debug.printInformation("best valid tuning => canMoveIndices => ", canMoveIndices[i]);
//                    Debug.printInformation("best valid tuning => reward => ", moveRewards[canMoveIndices[i]]);
                    bestValidDirection = canMoveIndices[i];
                    bestValidReward = moveRewards[canMoveIndices[i]];
                }
            }
        }

        /* Set communication for other slanderers if there is a muckraker within my range */
        Communication.checkAndSetFlag(flag);
//        Debug.printInformation("flag is set for ", flag);
//        Debug.printByteCode("runFromMuckrakerMove() => SET FLAG ");

        /* Below is based on movement */
        if (!controller.isReady()) return 1;

        if (foundEnemyMuckraker) {
            if (bestValidDirection != -1) {
                controller.move(Constants.DIRECTIONS[bestValidDirection]);
                return 2;
            }
            return 1;
        }

//        Debug.printByteCode("runFromMuckrakerMove() => DID NOT FIND ENEMY... ");

        /* No muckrakers were found, so we need to check the flags of nearby slanderer units instead */
        double closestLocation = 9998;
        int preferedMovementDirectionIdx = -1;

        for (RobotInfo robotInfo : Cache.ALL_NEARBY_FRIENDLY_ROBOTS) {
            if (robotInfo.getType() == RobotType.POLITICIAN) { //SLANDERERS THINK ALL SLANDERERS ARE POLITICIANS, so we need to check politicians here...
                double dist = Pathfinding.travelDistance(Cache.CURRENT_LOCATION, robotInfo.location)
                        + 0.01 * Cache.CURRENT_LOCATION.distanceSquaredTo(robotInfo.location);
                if (dist < closestLocation) { //the closest bot in danger to us is our biggest threat as well
                    int encodedFlag = controller.getFlag(robotInfo.ID);

//                    if (Debug.debug) System.out.println("DECODING FLAG " + encodedFlag + " for " + robotInfo.location);

                    if (Communication.decodeIsFlagMovementBotType(encodedFlag)) {
                        if (Communication.decodeMovementBotType(encodedFlag) == Constants.MOVEMENT_BOTS_TYPES.SLANDERER_TYPE &&
                                Communication.decodeMovementBotIsDanger(encodedFlag)) {

                            Constants.MOVEMENT_BOTS_DATA movementBotsData = Communication.decodeMovementBotData(encodedFlag);
                            preferedMovementDirectionIdx = Communication.convert_MovementBotData_DirectionInt(movementBotsData);
                            closestLocation = dist;
//                            if (Debug.debug) System.out.println("Correct type of flag, setting direction to " + preferedMovementDirectionIdx + " at dist " + dist);
                        }
                    }
                }
            }
        }

//        Debug.printByteCode("runFromMuckrakerMove() => ITERATED THROUGH FRIENDLY ROBOTS ");

        if (preferedMovementDirectionIdx != -1) {
            if (controller.canMove(Constants.DIRECTIONS[preferedMovementDirectionIdx])) {
                controller.move(Constants.DIRECTIONS[preferedMovementDirectionIdx]);
                return 2;
            } else if (controller.canMove(Constants.DIRECTIONS[(preferedMovementDirectionIdx + 1) % 8])) {
                controller.move(Constants.DIRECTIONS[(preferedMovementDirectionIdx + 1) % 8]);
                return 2;
            } else if (controller.canMove(Constants.DIRECTIONS[(preferedMovementDirectionIdx + 7) % 8])) {
                controller.move(Constants.DIRECTIONS[(preferedMovementDirectionIdx + 7) % 8]);
                return 2;
            }
            return 1;
        }
        return 0; // no reason to move
    }




    /* UNUSED ->
    Sets communciation to danger if any enemy bot is nearby
    Return: true if and only if there are any enemy bots nearby OR if any slanderer friendly robot has danger flag */
    private boolean checkIfInDanger() throws GameActionException {
        boolean inDanger = false;
        int flag = 0;
        if (Cache.ALL_NEARBY_ENEMY_ROBOTS.length > 0) {
            inDanger = true;
            flag = Communication.encode_MovementBotType_and_MovementBotData(Constants.MOVEMENT_BOTS_TYPES.SLANDERER_TYPE, Constants.MOVEMENT_BOTS_DATA.IN_DANGER_MOVE, true);
        }
        else {
            for (RobotInfo robotInfo : Cache.ALL_NEARBY_FRIENDLY_ROBOTS) {
                int encodedFlag = Communication.checkAndGetFlag(robotInfo.ID);
                if (Communication.decodeIsFlagMovementBotType(encodedFlag) &&
                        Communication.decodeMovementBotIsDanger(encodedFlag) &&
                        Communication.decodeMovementBotType(encodedFlag) == Constants.MOVEMENT_BOTS_TYPES.SLANDERER_TYPE) {
                    // friendly slanderer
                    if (Communication.decodeMovementBotData(encodedFlag) == Constants.MOVEMENT_BOTS_DATA.IN_DANGER_MOVE) {
                        inDanger = true;
                    }
                }
            }
        }
        Communication.checkAndSetFlag(flag);
        return inDanger;
    }


}
