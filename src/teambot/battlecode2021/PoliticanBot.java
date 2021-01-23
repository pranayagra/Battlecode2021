package teambot.battlecode2021;

import battlecode.common.*;
import teambot.RunnableBot;
import teambot.battlecode2021.util.*;

import java.util.Random;

public class PoliticanBot implements RunnableBot {
    private RobotController controller;
    private static Random random;
    private Pathfinding pathfinding;

    private MapLocation[] muckrakerLocations;
    private int[] muckrakerDistances;

    private int friendlySlanderersSize;

    private int triedCloserCnt;

    private boolean tickUpdateToPassiveAttacking;

    private int noEnemiesSeenCnt;
    private boolean defendType;

    private int bestDistanceOnAttack;
    private int numActionTurnsTaken;
    private int numRoundsTaken;

    public static final int HEALTH_DEFEND_UNIT = 18;

    //TODO (1/23): optimize bytecode, especially when on defense!
    //TODO: Politican bot upgrade movement (currently bugged and random movement) + fix explosion bug/optimize explosion radius

    public PoliticanBot(RobotController controller) throws GameActionException {
        this.controller = controller;

        init();
    }

    @Override
    public void init() throws GameActionException {
        this.pathfinding = new Pathfinding();
        pathfinding.init(controller);

        random = new Random (controller.getID());

        noEnemiesSeenCnt = 0;

        muckrakerLocations = new MapLocation[30];
        muckrakerDistances = new int[30];

        if (controller.getConviction() <= HEALTH_DEFEND_UNIT) defendType = true;

        tickUpdateToPassiveAttacking = false;
        bestDistanceOnAttack = 999999999;
        numActionTurnsTaken = 0;
        numRoundsTaken = 0;
    }


    /* Behavior =>
    Good Square => for locations greater than y coordinate: 4,8,12,16,20...  for locations less than y coordinate: 6,10,12,14,18...
    Bad Square => other squares
    Return: true if and only if the square is good
    */
    private boolean checkIfGoodSquare(MapLocation location) throws GameActionException {
        boolean valid = location.x % 2 == 1 && location.y % 2 == 0 && (location.x + location.y) % 4 == 1 && !location.isAdjacentTo(Cache.myECLocation);

        for (RobotInfo robotInfo : controller.senseNearbyRobots(location, 5, Cache.OUR_TEAM)) {
            if (robotInfo.type == RobotType.POLITICIAN) {
                if (controller.canGetFlag(robotInfo.ID)) {
                    int encodedFlag = controller.getFlag(robotInfo.ID);
                    if (CommunicationMovement.decodeIsSchemaType(encodedFlag) && CommunicationMovement.decodeMyUnitType(encodedFlag) == CommunicationMovement.MY_UNIT_TYPE.SL) {
                        valid = false;
                    }
                }
            }
        }

        return valid;
    }

    @Override
    public void turn() throws GameActionException {

        if (controller.getRoundNum() <= 150) noEnemiesSeenCnt = 0;
        if (Cache.NUM_ROUNDS_SINCE_SPAWN <= 50) noEnemiesSeenCnt = 0;

        if (Cache.myECLocation == null) {
            if (controller.isReady()) {
                controller.empower(RobotType.POLITICIAN.actionRadiusSquared);
            }
        }

        if (defendType) {
            // defend type
            noEnemiesSeenCnt++;
            for (RobotInfo robotInfo: Cache.ALL_NEARBY_ENEMY_ROBOTS) {
                if (robotInfo.type == RobotType.MUCKRAKER) noEnemiesSeenCnt = 0;
            }

            if (noEnemiesSeenCnt >= 75) {
                boolean switchToAttack = random.nextInt(10) + 1 <= 5;
                Debug.printInformation("SWITCH TO ATTACK: " + switchToAttack, Cache.EC_INFO_LOCATION);
                if (switchToAttack) {
                    defendType = false;
                    Cache.EC_INFO_LOCATION = null;
                }
                noEnemiesSeenCnt = 0;
            }
            setFlagToIndicateDangerToSlanderer();
            if (chaseMuckraker()) return;
            if (buildLattice()) return;

        } else {
            // attack poli/EC type
            if (Cache.EC_INFO_LOCATION != null && Cache.FOUND_ECS.get(Cache.EC_INFO_LOCATION) == null) {
                moveAndDestroyEC();
            } else if (Cache.EC_INFO_LOCATION != null && Cache.FOUND_ECS.get(Cache.EC_INFO_LOCATION) != CommunicationLocation.FLAG_LOCATION_TYPES.MY_EC_LOCATION) {
                moveAndDestroyEC();
            } else {

                //read EC flag attack to change attack location
                if (controller.canGetFlag(Cache.myECID)) {
                    int ECFlag = controller.getFlag(Cache.myECID);
                    if (CommunicationECSpawnFlag.decodeIsSchemaType(ECFlag)) {
                        CommunicationECSpawnFlag.ACTION action = CommunicationECSpawnFlag.decodeAction(ECFlag);
                        if (action == CommunicationECSpawnFlag.ACTION.ATTACK_LOCATION) {
                            Cache.EC_INFO_LOCATION = CommunicationECSpawnFlag.decodeLocationData(ECFlag);
                            Cache.EC_INFO_ACTION = action;
                            Cache.FOUND_ECS.remove(Cache.EC_INFO_LOCATION); // remove the location from my cache (something has changed since!)
                            Debug.printInformation("PASSIVE POLITICIAN GIVEN PURPOSE TO ATTACK! ", Cache.EC_INFO_LOCATION);
                            bestDistanceOnAttack = 999999999;
                            numActionTurnsTaken = 0;
                            numRoundsTaken = 0;
                            tickUpdateToPassiveAttacking = false;
                            return;
                        }
                    }
                }

                /* send a tick update to the EC ONCE that this bot is a free attacking bot not currently targeting any location */
                int flag = CommunicationHealth.encodeECInfo(true, true, CommunicationHealth.COMMUNICATION_UNIT_TEAM.CONVERTING_TO_PASSIVE_POLITICIAN, controller.getConviction());
                if (controller.canSetFlag(flag) && !tickUpdateToPassiveAttacking) {
                    if (!Comms.hasSetFlag) {
                        controller.setFlag(flag);
                        tickUpdateToPassiveAttacking = true;
                        Comms.hasSetFlag = true;
                        Debug.printInformation("SENDING URGENT PASSIVE TICK TO EC ", flag);
                    } else {
                        Comms.checkAndAddFlag(flag);
                    }
                }

                //TODO (1/21): change so it is outside our base + defenders always or something
                //TODO (1/21): make politicians (if they stumble upon an EC with low health) attack automatically
                if (controller.getConviction() <= HEALTH_DEFEND_UNIT) {
                    Debug.printInformation("ATTACKING UNIT PRETENDING TO DEFEND", " VALID ");
                    setFlagToIndicateDangerToSlanderer();
                    if (chaseMuckraker()) return;
                    if (buildLattice()) return;
                } else {
                    Direction random = Pathfinding.randomValidDirection();
                    if (random != null && controller.canMove(random)) controller.move(random);
                }
            }
        }

        Debug.printByteCode("END TURN POLI => ");
    }

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
            if (robotInfo.type == RobotType.POLITICIAN && robotInfo.conviction >= 11 && robotInfo.conviction <= HEALTH_DEFEND_UNIT && controller.canGetFlag(robotInfo.ID)) {
                int flag = controller.getFlag(robotInfo.ID);
                if (CommunicationMovement.decodeIsSchemaType(flag) &&
                        CommunicationMovement.decodeMyUnitType(flag) == CommunicationMovement.MY_UNIT_TYPE.SL) continue;

                //Debug.printInformation("MUCKRAKER LOCATIONS BEF " + muckrakerSize, Arrays.toString(muckrakerLocations));
                //Debug.printInformation("MUCKRAKER DISTANCES BEF " + muckrakerSize, Arrays.toString(muckrakerDistances));

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
                //Debug.printInformation("MUCKRAKER LOCATIONS AFT " + muckrakerSize, Arrays.toString(muckrakerLocations));
                //Debug.printInformation("MUCKRAKER DISTANCES AFT " + muckrakerSize, Arrays.toString(muckrakerDistances));
            }
        }

        //Debug.printInformation("MUCKRAKER LOCATIONS DONE " + muckrakerSize, Arrays.toString(muckrakerLocations));
        //Debug.printInformation("MUCKRAKER DISTANCES DONE " + muckrakerSize, Arrays.toString(muckrakerDistances));

        if (muckrakerSize == 0) return false;

        int target = 0; //target closest one to me
        for (int i = 1; i < muckrakerSize; ++i) {
            if (muckrakerDistances[target] > muckrakerDistances[i]) {
                target = i;
            }
        }

        MapLocation targetLocation = muckrakerLocations[target];
        //Debug.printInformation("target is " + target + " with location " + targetLocation + " and distance " + muckrakerDistances[target], " TARGET MUCKRAKER");

        if (!controller.isReady()) return false;

        /* Move towards the muckraker such that the adjacent square is as close as possible to the EC */
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
        Direction validDir = Pathfinding.toMovePreferredDirection(toMove, 2);

        if (controller.isReady()) {
            int minExplosionRadius = Cache.CURRENT_LOCATION.distanceSquaredTo(muckrakerLocations[target]);
            int friendlySize = controller.senseNearbyRobots(minExplosionRadius, Cache.OUR_TEAM).length;
            //Debug.printInformation("FRIENDLY SIZE IS " + friendlySize + " IN RANGE " + minExplosionRadius, " VALID?");
            if (friendlySize >= 3 || !controller.canEmpower(minExplosionRadius)) {
                int flag = CommunicationMovement.encodeMovement(true, true, CommunicationMovement.MY_UNIT_TYPE.PO, CommunicationMovement.MOVEMENT_BOTS_DATA.IN_DANGER_MOVE, CommunicationMovement.COMMUNICATION_TO_OTHER_BOTS.MOVE_AWAY_FROM_ME, false, false, 0);
                Comms.checkAndAddFlag(flag);
                //Debug.printInformation("MOVE " + validDir, " VALID?");
                if (validDir != null) controller.move(validDir);
                else Pathfinding.move(bestLocation);
                return true;
            } else {
                for (int i = RobotType.POLITICIAN.actionRadiusSquared; i >= minExplosionRadius; i -= 2) {
                    if (controller.senseNearbyRobots(i, Cache.OUR_TEAM).length <= 3) {
                        //Debug.printInformation("EXPLODING ", i);
                        controller.empower(i);
                        return true;
                    }
                }
                controller.empower(minExplosionRadius);
                return false;
            }
        }


//        Direction toMove = Cache.CURRENT_LOCATION.directionTo(bestLocation);
//        Direction validDir = Pathfinding.toMovePreferredDirection(toMove, 1);
//        if (Cache.CURRENT_LOCATION.distanceSquaredTo(muckrakerLocations[target]) <= 3) {
//            if (controller.canEmpower()) {
//                controller.empower(Cache.CURRENT_LOCATION.distanceSquaredTo(muckrakerLocations[target]));
//            }
//        }
//
//        if (validDir != null && toMove != Direction.CENTER) {
//            if (controller.canMove(validDir)) {
//                controller.move(validDir);
//            }
//            int miniDistance = 999;
//            MapLocation expectedLocation = Cache.CURRENT_LOCATION.add(validDir);
//
//            for (RobotInfo robotInfo : Cache.ALL_NEARBY_FRIENDLY_ROBOTS) {
//                int encodedFlag = controller.getFlag(robotInfo.ID);
//                if (CommunicationMovement.decodeIsSchemaType(encodedFlag)) {
//                    CommunicationMovement.MY_UNIT_TYPE myUnitType = CommunicationMovement.decodeMyUnitType(encodedFlag);
//                    if (myUnitType == CommunicationMovement.MY_UNIT_TYPE.SL) {
//                        int distance = Pathfinding.travelDistance(expectedLocation, robotInfo.location);
//                        miniDistance = Math.min(miniDistance, distance);
//                    }
//                }
//            }
//            if (miniDistance <= 4) {
//                controller.move(validDir);
//                //Debug.printInformation("MOVING FOR MUCKRAKER ", validDir);
//            }
//        }

        return false;
    }

    public void executeGoodSquare() throws GameActionException {

        if (!controller.isReady()) return;

        int moveTowardsDistance = Cache.CURRENT_LOCATION.distanceSquaredTo(Cache.myECLocation);
        Direction moveTowardsDirection = null;

        int[] dx = {2, 2, -2, -2};
        int[] dy = {2, -2, 2, -2};
        for (int i = 0; i < 4; ++i) {
            MapLocation candidateLocation = Cache.CURRENT_LOCATION.translate(dx[i], dy[i]);
            Direction moveDirection = Cache.CURRENT_LOCATION.directionTo(candidateLocation);
            int candidateDistance = candidateLocation.distanceSquaredTo(Cache.myECLocation);
            boolean isGoodSquare = checkIfGoodSquare(candidateLocation);


            if (isGoodSquare && candidateDistance < moveTowardsDistance && controller.canMove(moveDirection) && controller.canSenseLocation(candidateLocation) && controller.senseRobotAtLocation(candidateLocation) == null) {
                moveTowardsDirection = moveDirection;
                moveTowardsDistance = candidateDistance;
            }
        }

        if (moveTowardsDirection != null) {
            controller.move(moveTowardsDirection);
        }
    }

    public void executeBadSquare() throws GameActionException {

        if (!controller.isReady()) return;

        int badSquareMaximizedDistance = Cache.CURRENT_LOCATION.distanceSquaredTo(Cache.myECLocation);;
        Direction badSquareMaximizedDirection = null;

        // try to find a good square

        // move further or equal to EC

        int goodSquareMinimizedDistance = (int) 1E9;
        Direction goodSquareMinimizedDirection = null;

        for (int dx = -2; dx <= 2; ++dx) {
            for (int dy = -2; dy <= 2; ++dy) {
                MapLocation candidateLocation = Cache.CURRENT_LOCATION.translate(dx, dy);
                int candidateDistance = candidateLocation.distanceSquaredTo(Cache.myECLocation);
                Direction candidateDirection = Pathfinding.toMovePreferredDirection(Cache.CURRENT_LOCATION.directionTo(candidateLocation), 1);
                boolean isGoodSquare = checkIfGoodSquare(candidateLocation);

                if (!controller.canSenseLocation(candidateLocation) || candidateLocation.isAdjacentTo(Cache.myECLocation) ||
                        candidateDirection == null || !controller.canMove(candidateDirection) || controller.senseRobotAtLocation(candidateLocation) != null) {
                    continue;
                }

                if (isGoodSquare) {
                    if (goodSquareMinimizedDistance > candidateDistance) {
                        goodSquareMinimizedDistance = candidateDistance;
                        goodSquareMinimizedDirection = candidateDirection;
                    }
                } else {
                    if (badSquareMaximizedDistance <= candidateDistance) {
                        badSquareMaximizedDistance = candidateDistance;
                        badSquareMaximizedDirection = candidateDirection;
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
            Debug.printInformation("SLANDERER STUCK ON BAD SQUARE ",  " NO VALID GOOD SQUARE OR FURTHER BAD SQUARE");
        }

    }

    public boolean buildLattice() throws GameActionException {

        boolean isGoodSquare = checkIfGoodSquare(Cache.CURRENT_LOCATION);

        if (isGoodSquare) {
            executeGoodSquare();
        } else {
            executeBadSquare();
        }

        return true;

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

        Debug.printInformation("Moving towards EC", distance);

        if (bestDistanceOnAttack > distance) {
            bestDistanceOnAttack = distance;
            numActionTurnsTaken = 0;
            numRoundsTaken = 0;
        }

        if (numRoundsTaken >= 20 && numActionTurnsTaken >= 5) {
            //TODO: if distance has not gotten better, we need to mine our way through
            if (controller.senseNearbyRobots(1, Cache.OPPONENT_TEAM).length > 0 && controller.canEmpower(1)) {
                controller.empower(1);
                return true;
            }
        }

        if (distance > actionRadius - 2) {
            if (controller.isReady()) {
                numActionTurnsTaken++;
            }
            numRoundsTaken++;
            pathfinding.move(Cache.EC_INFO_LOCATION);
            return true;
        }

        RobotInfo ECInfo = controller.senseRobotAtLocation(Cache.EC_INFO_LOCATION);
        if (ECInfo.team.equals(Cache.OUR_TEAM)) {
            return false;
        }

        int ourTeamSize = controller.senseNearbyRobots(distance, Cache.OUR_TEAM).length;

        // GET CLOSER TO OPPONENT (move away from enemy units). In a tie, move closer to EC
        int bestSize = controller.senseNearbyRobots(distance, Cache.OPPONENT_TEAM).length;
        MapLocation getCloser = null;
        for (Direction direction : Constants.CARDINAL_DIRECTIONS) {
            MapLocation candidateLocation = Cache.EC_INFO_LOCATION.add(direction);
            if (controller.canSenseLocation(candidateLocation) && !controller.isLocationOccupied(candidateLocation)) {
                int trySize = controller.senseNearbyRobots(candidateLocation, 1, Cache.OPPONENT_TEAM).length;
                if (trySize < bestSize || (ourTeamSize > 0 && distance > 1)) {
                    bestSize = trySize;
                    getCloser = candidateLocation;
                }
            }
        }

        if (getCloser != null && triedCloserCnt <= 15) {
            triedCloserCnt++;
            pathfinding.move(getCloser);
            return true;
        }

        // WAIT FOR OUR TEAM TO MOVE AWAY

        if (ourTeamSize > 0 && triedCloserCnt <= 10) {
            ++triedCloserCnt;
            return false;
        }

        ECExists = true;

        if (controller.getConviction() <= HEALTH_DEFEND_UNIT) {
            if (controller.canEmpower(RobotType.POLITICIAN.actionRadiusSquared)) {
                controller.empower(RobotType.POLITICIAN.actionRadiusSquared);
                return true;
            }
        } else if (controller.canEmpower(distance)) {
            controller.empower(distance);
            return true;
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
    public void setFlagToIndicateDangerToSlanderer() throws GameActionException {

        friendlySlanderersSize = 0;

        for (RobotInfo robotInfo : Cache.ALL_NEARBY_FRIENDLY_ROBOTS) {
            if (robotInfo.type == RobotType.POLITICIAN) {
                if (controller.canGetFlag(robotInfo.ID)) {
                    int encodedFlag = controller.getFlag(robotInfo.ID);
                    if (CommunicationMovement.decodeIsSchemaType(encodedFlag) && CommunicationMovement.decodeMyUnitType(encodedFlag) == CommunicationMovement.MY_UNIT_TYPE.SL) {
                        // This is a slanderer on our team...
                        friendlySlanderersSize++;
                    }
                }
            }
        }

        MapLocation locationToWarnAbout = null;
        int leastDistance = 9999;
        for (RobotInfo robotInfo : Cache.ALL_NEARBY_ENEMY_ROBOTS) {
            if (robotInfo.type == RobotType.MUCKRAKER) {
                int candidateDistance = Cache.CURRENT_LOCATION.distanceSquaredTo(robotInfo.location);

                if (leastDistance > candidateDistance) {
                    leastDistance = candidateDistance;
                    locationToWarnAbout = robotInfo.location;
                }
            }
        }

        int flag = CommunicationMovement.encodeMovement(true, true,
                CommunicationMovement.MY_UNIT_TYPE.PO, CommunicationMovement.MOVEMENT_BOTS_DATA.NOOP,
                CommunicationMovement.COMMUNICATION_TO_OTHER_BOTS.NOOP, false, false, 0);

        if (locationToWarnAbout != null && friendlySlanderersSize > 0) { //If I have both a muckraker and slanderer in range of me, alert danger by inDanger to slanderers!
            Direction dangerDirection = Cache.myECLocation.directionTo(locationToWarnAbout);
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


    }




}
