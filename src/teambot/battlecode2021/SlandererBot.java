package teambot.battlecode2021;

import battlecode.common.*;
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

    public SlandererBot(RobotController controller) throws GameActionException {
        this.controller = controller;
        init();
    }

    @Override
    public void init() throws GameActionException {
        this.pathfinding = new Pathfinding();
        pathfinding.init(controller);

        moveLocs = new MapLocation[9];
        moveRewards = new double[9];
        canMoveIndices = new int[9];
    }

    @Override
    public void turn() throws GameActionException {

        // set flag or read EC/nearby flags or something

//        if (!controller.isReady()) return;

        switch (runFromMuckrakerMove()) {
            case 0:
                Pathfinding.move(Pathfinding.randomLocation());
                Pathfinding.move(Pathfinding.randomLocation());
                Pathfinding.move(Pathfinding.randomLocation());
                Pathfinding.move(Pathfinding.randomLocation());
                break;
            default:
                break;
        }

        // maybe do something else (not sure what)


    }


    // return: 0 means we are not moving because there is no reason to move, 1 means we are not moving because we shouldn't move (or can't), 2 means we are moving
    public int runFromMuckrakerMove() throws GameActionException {


        //flag indicates best direction to move, not direction I am moving...
        Debug.printByteCode("runFromMuckrakerMove() => before run " + controller.getCooldownTurns());

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

        Debug.printByteCode("runFromMuckrakerMove() => computed all valid dirs + locations ");
        Debug.printInformation("valid directions is sized " + canMoveIndicesSize + " and has ", Arrays.toString(canMoveIndices));

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


        Debug.printInformation("my location reward is ", rewardOfStaying);
        Debug.printInformation("location rewards surrounding me is ", Arrays.toString(moveRewards));
        Debug.printByteCode("runFromMuckrakerMove() => SCANNED ENEMY LOCATIONS ");
        int flag = 0;
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

            if (bestDirection != -1) {
                flag = Communication.encode_ExtraANDLocationType_and_ExtraANDLocationData(
                        Constants.FLAG_EXTRA_TYPES.DIRECTION_MOVING, Constants.FLAG_LOCATION_TYPES.NOOP, bestDirection, Cache.CURRENT_LOCATION);
            }

            for (int i = 0; i < canMoveIndicesSize; ++i) {
                if (moveRewards[canMoveIndices[i]] > bestValidReward) {
                    Debug.printInformation("best valid tuning => canMoveIndices => ", canMoveIndices[i]);
                    Debug.printInformation("best valid tuning => reward => ", moveRewards[canMoveIndices[i]]);
                    bestValidDirection = canMoveIndices[i];
                    bestValidReward = moveRewards[canMoveIndices[i]];
                }
            }
        }

        /* Set communication for other slanderers if there is a muckraker within my range */
        Communication.checkAndSetFlag(flag);
        Debug.printInformation("flag is set for ", flag);
        Debug.printByteCode("runFromMuckrakerMove() => SET FLAG ");

        /* Below is based on movement */
        if (!controller.isReady()) return 1;

        if (foundEnemyMuckraker) {
            if (bestValidDirection != -1) {
                controller.move(Constants.directions[bestValidDirection]);
                return 2;
            }
            return 1;
        }

        Debug.printByteCode("runFromMuckrakerMove() => DID NOT FIND ENEMY... ");

        /* No muckrakers were found, so we need to check the flags of nearby slanderer units instead */
        double closestLocation = 9998;
        int preferedMovementDirectionIdx = -1;

        for (RobotInfo robotInfo : Cache.ALL_NEARBY_FRIENDLY_ROBOTS) {
            if (robotInfo.getType() == RobotType.POLITICIAN) { //SLANDERERS THINK ALL SLANDERERS ARE POLITICIANS, so we need to check politicians here...
                double dist = Pathfinding.travelDistance(Cache.CURRENT_LOCATION, robotInfo.location)
                        + 0.01 * Cache.CURRENT_LOCATION.distanceSquaredTo(robotInfo.location);
                if (dist < closestLocation) { //the closest bot in danger to us is our biggest threat as well
                    int encodedFlag = controller.getFlag(robotInfo.ID);

                    if (Debug.debug) System.out.println("DECODING FLAG " + encodedFlag + " for " + robotInfo.location);

                    if (Communication.decodeIsFlagLocationType(encodedFlag, false)) {
                        if (Communication.decodeExtraType(encodedFlag) == Constants.FLAG_EXTRA_TYPES.DIRECTION_MOVING) {
                            preferedMovementDirectionIdx = Communication.decodeExtraData(encodedFlag);
                            if (Debug.debug) System.out.println("Correct type of flag, setting direction to " + preferedMovementDirectionIdx + " at dist " + dist);
                            closestLocation = dist;
                        }
                    }
                }
            }
        }

        Debug.printByteCode("runFromMuckrakerMove() => ITERATED THROUGH FRIENDLY ROBOTS ");

        if (preferedMovementDirectionIdx != -1) {
            if (controller.canMove(Constants.directions[preferedMovementDirectionIdx])) {
                controller.move(Constants.directions[preferedMovementDirectionIdx]);
                return 2;
            } else if (controller.canMove(Constants.directions[(preferedMovementDirectionIdx + 1) % 8])) {
                controller.move(Constants.directions[(preferedMovementDirectionIdx + 1) % 8]);
                return 2;
            } else if (controller.canMove(Constants.directions[(preferedMovementDirectionIdx + 7) % 8])) {
                controller.move(Constants.directions[(preferedMovementDirectionIdx + 7) % 8]);
                return 2;
            }
            return 1;
        }
        return 0; // no reason to move

    }
}
