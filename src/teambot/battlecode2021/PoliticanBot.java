package teambot.battlecode2021;

import battlecode.common.*;
import teambot.RunnableBot;
import teambot.battlecode2021.util.*;

import java.util.Arrays;
import java.util.Random;

public class PoliticanBot implements RunnableBot {
    private RobotController controller;
    private static Random random;
    private Pathfinding pathfinding;

    private MapLocation[] muckrakerLocations;
    private int[] muckrakerDistances;
    private int[] muckrakerConviction;

    private MapLocation[] slanderLocations;
    private int slanderSize;

    private boolean defendType;

    /* DEFENSIVE Variables */
    private int numRoundsStuckOnBadSquare;
    public static final int HEALTH_DEFEND_UNIT = 18;
    private int noEnemiesSeenCnt;
    private MapLocation targetWhenStuck = null;

    /* OFFENSIVE Variables */
    private boolean circleDirectionClockwise;
    private int decreaseScoreThresholdAmount;

    private int bestDistanceOnAttack;
    private int numActionTurnsTaken;
    private int numRoundsTaken;

    /* OFFENSIVE -> PASSIVE Variable */
    private boolean tickUpdateToPassiveAttacking;

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

        circleDirectionClockwise = false;
        decreaseScoreThresholdAmount = 0;

        muckrakerLocations = new MapLocation[30];
        muckrakerDistances = new int[30];
        muckrakerConviction = new int[30];

        slanderLocations = new MapLocation[50];

        if (controller.getConviction() <= HEALTH_DEFEND_UNIT) defendType = true;
        if (controller.getConviction() == 61) defendType = true;

        tickUpdateToPassiveAttacking = false;
        bestDistanceOnAttack = 999999999;
        numActionTurnsTaken = 0;
        numRoundsTaken = 0;

        numRoundsStuckOnBadSquare = 0;

    }

    private void precomputeSlandererLocations() throws GameActionException {
        slanderSize = 0;
        for (RobotInfo robotInfo : Cache.ALL_NEARBY_FRIENDLY_ROBOTS) {
            if (controller.canGetFlag(robotInfo.ID)) {
                int encodedFlag = controller.getFlag(robotInfo.ID);
                if (CommunicationMovement.decodeIsSchemaType(encodedFlag) && CommunicationMovement.decodeMyUnitType(encodedFlag) == CommunicationMovement.MY_UNIT_TYPE.SL) {
                    slanderLocations[slanderSize++] = robotInfo.location;
                }
            }
        }
    }

    /* Behavior =>
    Good Square => mod math below + not adjacent to EC + away from slanderers
    Bad Square => other squares
    Return: true if and only if the square is good
    */
    private boolean checkIfGoodSquare(MapLocation location) {

        boolean valid = location.x % 2 == 1 && location.y % 2 == 0 && (location.x + location.y) % 4 == 1 && !location.isAdjacentTo(Cache.myECLocation);

        if (!valid) return false;

        for (int i = 0; i < slanderSize; ++i) {
            if (location.distanceSquaredTo(slanderLocations[i]) <= 5) {
                return false;
            }
        }

        return true;
    }

    @Override
    public void turn() throws GameActionException {
        executeTurn();
        Debug.printByteCode("END => ");
    }

    public void executeTurn() throws GameActionException {

        if (controller.getRoundNum() >= 1490) {
            // we are losing on votes potentially
            if (controller.getTeamVotes() < 750) {
                if (Cache.ALL_NEARBY_ENEMY_ROBOTS.length > 0) {
                    if (controller.canEmpower(RobotType.POLITICIAN.actionRadiusSquared)) {
                        controller.empower(RobotType.POLITICIAN.actionRadiusSquared);
                    }
                } else {
                    Pathfinding.move(Pathfinding.randomLocation());
                }
                return;
            }
        }

        if (controller.getRoundNum() <= 175) noEnemiesSeenCnt = 0;
        if (Cache.NUM_ROUNDS_SINCE_SPAWN <= 50) noEnemiesSeenCnt = 0;

        if (Cache.myECLocation == null || (2 <= controller.getInfluence() && controller.getInfluence() <= 10)) {
            if (controller.isReady()) {
                controller.empower(RobotType.POLITICIAN.actionRadiusSquared);
            }
        }

        if (defendType) {

            precomputeSlandererLocations();
            setFlagToIndicateDangerToSlanderer();

            noEnemiesSeenCnt++;
            for (RobotInfo robotInfo: Cache.ALL_NEARBY_ENEMY_ROBOTS) {
                if (robotInfo.type == RobotType.MUCKRAKER) {
                    noEnemiesSeenCnt = 0;
                    break;
                }
            }

            if (noEnemiesSeenCnt >= 75) {

                boolean switchToAttack = true;
                if (Math.abs(Cache.CURRENT_LOCATION.x - Cache.myECLocation.x) <= 11 || Math.abs(Cache.CURRENT_LOCATION.y - Cache.myECLocation.y) <= 11) {
                    switchToAttack = false;
                }

                boolean hasDefenseBehind = false;
                int myECDist = Cache.CURRENT_LOCATION.distanceSquaredTo(Cache.myECLocation);
                for (RobotInfo robotInfo: Cache.ALL_NEARBY_FRIENDLY_ROBOTS) {
                    if (robotInfo.type == RobotType.POLITICIAN && robotInfo.conviction <= HEALTH_DEFEND_UNIT) {
                        int otherDefendingPoliDistance = robotInfo.location.distanceSquaredTo(Cache.myECLocation);
                        if (otherDefendingPoliDistance <= myECDist) {
                            hasDefenseBehind = true;
                            break;
                        }
                    }
                }

                switchToAttack &= (random.nextInt(2) == 1);

//                Debug.printInformation("SWITCH TO ATTACK?: " + switchToAttack, Cache.EC_INFO_LOCATION);
                if (switchToAttack && hasDefenseBehind) {
                    defendType = false;
                    Cache.EC_INFO_LOCATION = null;
                }
                noEnemiesSeenCnt = 0;
            }

            if (chaseMuckraker()) return;
            if (buildLattice()) return;

        } else {
            // attack poli/EC type

            boolean attackEC = true;
            if (Cache.EC_INFO_LOCATION != null) {
                if (controller.canSenseLocation(Cache.EC_INFO_LOCATION)) {
                    RobotInfo robotInfo = controller.senseRobotAtLocation(Cache.EC_INFO_LOCATION);
                    if (robotInfo.team == Cache.OUR_TEAM) {
                        // the attacking location is now on our team
                        attackEC = false;
                    }
                }
            }

            if (attackEC && Cache.EC_INFO_LOCATION != null && Cache.FOUND_ECS.get(Cache.EC_INFO_LOCATION) == null) {
                attackECProtocol();
            } else if (attackEC && Cache.EC_INFO_LOCATION != null && Cache.FOUND_ECS.get(Cache.EC_INFO_LOCATION) != CommunicationLocation.FLAG_LOCATION_TYPES.MY_EC_LOCATION) {
                attackECProtocol();
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
                    } else {
                        Comms.checkAndAddFlag(flag);
                    }
                }


                if (controller.getConviction() <= HEALTH_DEFEND_UNIT) {
//                    Debug.printInformation("ATTACKING UNIT PRETENDING TO DEFEND", " VALID ");
                    setFlagToIndicateDangerToSlanderer();
                    if (chaseMuckraker()) return;
                    if (buildLattice()) return;
                } else {
                    Direction random = Pathfinding.randomValidDirection();
                    if (random != null && controller.canMove(random)) controller.move(random);
                }
            }
        }

    }

    //for all muckrakers in my range, see which ones im closest too when compared to all polis =>
    // then calculate a threat level for all of them =>
    // approach closest such that you are directly in front of it (NWSE, closest to closest slanderer)
    public boolean chaseMuckraker() throws GameActionException {

        MapLocation targetLocation;
        int muckrakerSize = 0;
        //quickly find closest one to you
        //get list of muckraker locations + distance to them (travelDistance)
        for (RobotInfo robotInfo : Cache.ALL_NEARBY_ENEMY_ROBOTS) {
            if (robotInfo.type == RobotType.MUCKRAKER) {
                muckrakerLocations[muckrakerSize] = robotInfo.location;
                muckrakerConviction[muckrakerSize] = robotInfo.conviction;
                muckrakerDistances[muckrakerSize++] = Pathfinding.travelDistance(robotInfo.location, Cache.CURRENT_LOCATION);
            }
        }

        if (muckrakerSize == 0) return false;

        if (Cache.CONVICTION <= HEALTH_DEFEND_UNIT) {

            //TODO: This section seems bugged? what if first enemy is closer to friendly but all others are not.
            // Seems to work in practice though so low priority
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

            targetLocation = muckrakerLocations[target];
        }
        else {
            // Expensive politician
            // Greedily attack most expensive muckraker
            int target = 0;
            for (int i = 1; i < muckrakerSize; ++i) {
                if (muckrakerConviction[i] > muckrakerConviction[target]) {
                    target = i;
                }
            }


            // Don't attack if nearby friendly cheap politician
            if (muckrakerConviction[target] <= 2) {
                for (RobotInfo robotInfo : Cache.ALL_NEARBY_FRIENDLY_ROBOTS) { 
                    if (robotInfo.type == RobotType.POLITICIAN && robotInfo.conviction >= 11 && robotInfo.conviction <= HEALTH_DEFEND_UNIT && controller.canGetFlag(robotInfo.ID)) {
                        int flag = controller.getFlag(robotInfo.ID);
                        if (CommunicationMovement.decodeIsSchemaType(flag) &&
                                CommunicationMovement.decodeMyUnitType(flag) == CommunicationMovement.MY_UNIT_TYPE.SL) continue;
                        int distance = Pathfinding.travelDistance(robotInfo.location, muckrakerLocations[target]);
                        if (distance <= 25) {
                            return false;
                        }
                    }
                }
            }

            targetLocation = muckrakerLocations[target];
        }

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
            int minExplosionRadius = Cache.CURRENT_LOCATION.distanceSquaredTo(targetLocation);
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
            //move onto good square
            controller.move(goodSquareMinimizedDirection);
            numRoundsStuckOnBadSquare = 0;
        } else if (numRoundsStuckOnBadSquare >= 20) {
            //follow edge of unvisited when stuck
            Pathfinding.move(targetWhenStuck);
        } else if (badSquareMaximizedDirection != null) {
            //move onto bad square in hopes to find good square later
            controller.move(badSquareMaximizedDirection);
            numRoundsStuckOnBadSquare = 0;
        } else {
            // stuck, forfeit turn
            numRoundsStuckOnBadSquare++;
            if (numRoundsStuckOnBadSquare == 20) {
                Cache.CURRENT_LOCATION = controller.getLocation();
                int ranCounter = 0;
                if (Cache.MAP_TOP == 0) ++ranCounter;
                if (Cache.MAP_RIGHT == 0) ++ranCounter;
                if (Cache.MAP_BOTTOM == 0) ++ranCounter;
                if (Cache.MAP_LEFT == 0) ++ranCounter;

                if (ranCounter == 0) {
                    targetWhenStuck = Pathfinding.randomLocation();
                } else {
                    int randomDirection = random.nextInt(ranCounter) + 1; //[1,...,#of options]
                    if (Cache.MAP_TOP == 0 && --randomDirection == 0) {
                        targetWhenStuck = Cache.CURRENT_LOCATION.translate(0,64);
                    }
                    if (Cache.MAP_RIGHT == 0 && --randomDirection == 0) {
                        targetWhenStuck = Cache.CURRENT_LOCATION.translate(64,0);
                    }
                    if (Cache.MAP_BOTTOM == 0 && --randomDirection == 0) {
                        targetWhenStuck = Cache.CURRENT_LOCATION.translate(0,-64);
                    }
                    if (Cache.MAP_LEFT == 0 && --randomDirection == 0) {
                        targetWhenStuck = Cache.CURRENT_LOCATION.translate(-64,0);
                    }
                }
                if (targetWhenStuck == null) targetWhenStuck = Pathfinding.randomLocation();
//                Debug.printInformation("STUCK DEFENDER. MOVING TOWARDS " + targetWhenStuck, " VALID ");
            }
        }

    }

    public boolean buildLattice() throws GameActionException {

        boolean isGoodSquare = checkIfGoodSquare(Cache.CURRENT_LOCATION);
        Debug.printInformation("isGoodSquare ", isGoodSquare);
        if (isGoodSquare) {
            executeGoodSquare();
        } else {
            executeBadSquare();
        }

        return true;

    }


    private static int[][] importantLocationsToKill = {{0, 2}, {1, 1}, {2, 0}, {1, -1}, {0, -2}, {-1, -1}, {-2, 0}, {-1, 1}};
    private static int[] health = new int[8];
    private static int[] distances = new int[8];
    private static boolean[] poliType = new boolean[8];
    private static int[] myUnitDamages = new int[4]; // * 2 - 1, * 2, * 2 + 1

    private void updateImportantLocationsKills() throws GameActionException {

        for (int i = 0; i < 8; ++i) {
            // the only ones that I add are the ones that I know I can do something about (kill)
            health[i] = 9999;
            distances[i] = 9999;
            poliType[i] = false;
            MapLocation location = Cache.EC_INFO_LOCATION.translate(importantLocationsToKill[i][0], importantLocationsToKill[i][1]);
            int distanceFromMe = Cache.CURRENT_LOCATION.distanceSquaredTo(location);
            if (controller.canSenseLocation(location)) {
                RobotInfo robotInfo = controller.senseRobotAtLocation(location);

                // can't sense location (assume strong enemy), the location is empty (free damage), friendly unit (assume strong enemy), enemy unit (set values)
                if (robotInfo == null) {
                    health[i] = -1;
                    distances[i] = 0;
                } else {
                    if (robotInfo.team != Cache.OUR_TEAM) {
                        health[i] = robotInfo.conviction;
                        distances[i] = distanceFromMe;
                        controller.setIndicatorDot(location, 0, 0, 255);
                        if (robotInfo.type == RobotType.POLITICIAN) {
                            poliType[i] = true;
                        }
                    }
                }
            }
        }

        int i = 0;
        for (Direction direction : Constants.CARDINAL_DIRECTIONS) {
            myUnitDamages[i] = 0;
            MapLocation location = Cache.EC_INFO_LOCATION.add(direction);
            if (controller.canSenseLocation(location)) {
                RobotInfo robotInfo = controller.senseRobotAtLocation(location);
                if (robotInfo != null && robotInfo.type == RobotType.POLITICIAN && robotInfo.team == Cache.OUR_TEAM) {
                    myUnitDamages[i] = Math.max(0, robotInfo.conviction - 10);
                }
            }
            i += 1;
        }

        Debug.printInformation("myUnitDamages: " + Arrays.toString(myUnitDamages) + ", health: " + Arrays.toString(health) + ", distances: " + Arrays.toString(distances), " STORING INFORMATION FOR LATER");

    }

    public double calculateScore(int empowerDistanceSquared) {
        int myDamageToEC = 0;
        int ECHealth = 9999999;
        int numUnits = 0;
        for (RobotInfo robotInfo : Cache.ALL_NEARBY_ROBOTS) {
            int distance = Cache.CURRENT_LOCATION.distanceSquaredTo(robotInfo.location);
            if (distance <= empowerDistanceSquared) {
                ++numUnits;
            }
        }

        if (numUnits == 0) return -9999;

        //(int) ((((double)(conviction - 10)) / numBots) * buff)

        double convictionToGive = Math.max(0, controller.getConviction() - 10);
        int convictionPerBot = (int) ((convictionToGive / numUnits) * controller.getEmpowerFactor(Cache.OUR_TEAM,0));

        if (convictionPerBot == 0) return -9999;

        int unitsKilled = 0;

        for (RobotInfo robotInfo : Cache.ALL_NEARBY_ROBOTS) {

            if (robotInfo.team == Cache.OUR_TEAM) continue;

            int distance = Cache.CURRENT_LOCATION.distanceSquaredTo(robotInfo.location);
            if (distance <= empowerDistanceSquared) {
                if (robotInfo.type == RobotType.ENLIGHTENMENT_CENTER) {
                    ECHealth = robotInfo.conviction;
                    myDamageToEC = convictionPerBot;
                }

                if (robotInfo.conviction < convictionPerBot) {
                    ++unitsKilled;
                }
            }
        }

        //calculate 3-location, see how many will survive, and do damage / (1 + surival)
        int totalECDamageFromPrimeBots = 0;
        int totalECDamageImprovementsOfPrimeBots = 0;
        int totalECDamageBeforePrimeBots = 0;
        for (int i = 0; i < 4; ++i) {
            int leftSide = (i * 2 + 7) % 8;
            int centerSide = (i * 2);
            int rightSide = (i * 2 + 1) % 8;

            double convictionToGive_PrimeBot = myUnitDamages[i];
            if (convictionToGive_PrimeBot == 0) continue; //not a unit capable of attacking

            int numUnitsBeforeExplosion = 4;
            int numUnitsAfterExplosion = 4;

            if (health[leftSide] == -1) {
                --numUnitsBeforeExplosion;
            }
            if (health[centerSide] == -1) {
                --numUnitsBeforeExplosion;
            }
            if (health[rightSide] == -1) {
                --numUnitsBeforeExplosion;
            }

            if (distances[leftSide] <= empowerDistanceSquared) { //will impact explosion
                if (health[leftSide] < convictionPerBot) {
                    --numUnitsAfterExplosion;
                }
            }

            if (distances[centerSide] <= empowerDistanceSquared) {
                if (health[centerSide] < convictionPerBot) {
                    --numUnitsAfterExplosion;
                }
            }

            if (distances[rightSide] <= empowerDistanceSquared) {
                if (health[rightSide] < convictionPerBot) {
                    --numUnitsAfterExplosion;
                }
            }

            int convictionPerBot_PrimeBot_Before = (int) ((convictionToGive_PrimeBot / numUnitsBeforeExplosion) * controller.getEmpowerFactor(Cache.OUR_TEAM,2));
            int convictionPerBot_PrimeBot_After = (int) ((convictionToGive_PrimeBot / numUnitsAfterExplosion) * controller.getEmpowerFactor(Cache.OUR_TEAM,2));

            totalECDamageBeforePrimeBots += convictionPerBot_PrimeBot_Before;
            totalECDamageFromPrimeBots += convictionPerBot_PrimeBot_After;

            int convictionImprovement = convictionPerBot_PrimeBot_After - convictionPerBot_PrimeBot_Before;
            totalECDamageImprovementsOfPrimeBots += convictionImprovement;

            Debug.printInformation("For EC bomb on side " + i + ", numUnitsBeforeExplosion (max): " + numUnitsBeforeExplosion + ", numUnitsAfterExplosion (max): " + numUnitsAfterExplosion + ", convictionPerBot_PrimeBot_After: " + convictionPerBot_PrimeBot_After + ", convictionImprovement: " + convictionImprovement, " EC DAMAGE FOR POSITION " + i);
        }

        Debug.printInformation("totalECDamageFromPrimeBots: " + totalECDamageFromPrimeBots + ", totalECDamageImprovementsOfPrimeBots: " + totalECDamageImprovementsOfPrimeBots + ", myDamageToEC: " + myDamageToEC, " INFO IF I EXPLODE ");

        double score = unitsKilled * 3;

        // IFF EXPLODE WITH OTHERS THEN CAPTURES THE EC!
        if ( (totalECDamageFromPrimeBots + myDamageToEC) > ECHealth && totalECDamageBeforePrimeBots <= ECHealth) {
            score += 99999;
        }

        /* If I boost the damage by more than my damage */
        double totalECImprovedDamageOverall = totalECDamageImprovementsOfPrimeBots + myDamageToEC;
        double effectiveness = totalECImprovedDamageOverall / (controller.getConviction() + 1);
        score += (controller.getConviction() * Math.max(0, (effectiveness - 0.4)));

        /* We rather these units we univerally helpful in getting rid of clutter*/
        if (unitsKilled == 0) {
            score -= 1;
        }

        Debug.printInformation("EMPOWER DISTANCE " + empowerDistanceSquared + ": [unitsKilled: " + unitsKilled + ", totalECImprovedDamageOverall: " + totalECImprovedDamageOverall + ", effectiveness: " + effectiveness + "] => SCORE ", score);
        System.out.println("\n");
        return score;
    }


    /* ATTACKING CODE 1/24 (more for enemy EC) */

    //TODO: bug --> unit thinks for 1 turn that EC is still enemies -- maybe idk, not a huge deal I think tho?

    private static double bestScore;
    private static double scoreThreshold;
    private static int bestExplosionRadius;
    public void attackECProtocol() throws GameActionException {
        int distanceFromECToAttack = Cache.CURRENT_LOCATION.distanceSquaredTo(Cache.EC_INFO_LOCATION);

        if (!controller.isReady()) return;

        if (distanceFromECToAttack == 1) {
            decreaseScoreThresholdAmount = 0;
            if (worthExplodingEC()) return; //TODO: we should wait but also not wait forever...
            if (leaveSpot()) return; //TODO (IMP): maybe instead of leaving it's worth to just explode first?
        } else if (distanceFromECToAttack <= 20) { //TODO: not sure if this number 20 is best value
            decreaseScoreThresholdAmount++;
            updateImportantLocationsKills();
            if (decreaseScoreThresholdAmount <= 10) {
                updateExplosionScores(-999);
            } else {
                updateExplosionScores((decreaseScoreThresholdAmount - 10)); //sets bestScore and returns best radius
            }

            /* PERFORM EARLY ATTACK IFF WE KILL EC AND IT IS A SINGLE UNIT IN RANGE */
            if (bestScore >= 50000 && controller.senseNearbyRobots(distanceFromECToAttack).length == 1) {
                if (controller.canEmpower(bestExplosionRadius)) {
                    controller.empower(bestExplosionRadius);
                    return;
                }
            }

            if (moveToEmptySpot()) return; //TODO: not complete method yet (may get stuck for long time) -- do it based on process too -- not likely for bug to occur for long many rounds

            if (bestScore >= scoreThreshold && bestScore >= 1) {
                if (controller.canEmpower(bestExplosionRadius)) {
                    controller.empower(bestExplosionRadius);
                    return;
                }
            }

            if (circleEnemyEC()) return;

        } else {
            //move closer. If we have not gained distance in ~20 rounds AND ~4 potential moves, then we should consider exploding & decreasing the threshold as time goes on.
            // IF NOT STUCK => DO NOT EXPLODE
            decreaseScoreThresholdAmount = 0;
            if (bestDistanceOnAttack > distanceFromECToAttack) {
                bestDistanceOnAttack = distanceFromECToAttack;
                numActionTurnsTaken = 0;
                numRoundsTaken = 0;
            } else {
                numRoundsTaken++;
                if (controller.isReady()) numActionTurnsTaken++;
            }

            boolean isStuck = false;
            if (numRoundsTaken >= 20 && numActionTurnsTaken >= 4) {
                isStuck = true;
            }

            /* If we have made progress towards our target in the last 20 rounds AND 4 cooldown moves, then we are not stuck and continue moving towards the target. */
            if (!isStuck) {
                pathfinding.move(Cache.EC_INFO_LOCATION); return;
            }

            /* We have tried moving towards the target but have not made any progress in the last 20 rounds AND 4 cooldown moves. Let us start trying to explode and slowly decrease the threshold */
            if (isStuck) {
                updateImportantLocationsKills();
                updateExplosionScores((numRoundsTaken - 20) / 4);
                if (bestScore >= scoreThreshold && bestScore >= 1) {
                    if (controller.canEmpower(bestExplosionRadius)) {
                        controller.empower(bestExplosionRadius);
                        return;
                    }
                }
            }

            /* We did not cross the threshold on explosion. Just try moving again */
            pathfinding.move(Cache.EC_INFO_LOCATION); return;
        }

    }

    //change muck behavior --> if close to non-friendly EC + strong poli near it, go away from EC
    //change flag urgency in scout on poli --> add as extra bit for health (and remove urgency)
    //change / add a new flag that indicates if a slanderer has been found by poli or scout? more urgency as a tick?
    //make mucks target ECs iff no slanderer

    public boolean worthExplodingEC() throws GameActionException {
        //check 4 spots N W S E of location to attack --> calculate damage --> see if to explode (check cooldown maybe + buff)

        //TODO (not as important): improvements --> add some type of cooldown checker (a simple implimentation will be to use the cooldown of the newly entered bot
        int minDamage = 0;
        int maxDamage = 0;

        int myUnits = 0;

        double empowerFactor = controller.getEmpowerFactor(Cache.OUR_TEAM, 0);;

        for (Direction direction : Constants.CARDINAL_DIRECTIONS) {
            MapLocation checkStrongPoliticianLocation = Cache.EC_INFO_LOCATION.add(direction);

            if (checkStrongPoliticianLocation.equals(Cache.CURRENT_LOCATION)) {
                double currentTotalDamage = Math.max(0, controller.getConviction() - 10);
                int currentUnitsNear = controller.senseNearbyRobots(1).length;
                int damagePerUnit = (int) ((currentTotalDamage / currentUnitsNear) * empowerFactor);
                minDamage += damagePerUnit;
                maxDamage += damagePerUnit; //NOT a typo (maxDamage += damagePerUnit)
                myUnits = currentUnitsNear;
            } else if (controller.canSenseLocation(checkStrongPoliticianLocation)) {
                RobotInfo robotInfo = controller.senseRobotAtLocation(checkStrongPoliticianLocation);
                if (robotInfo != null && robotInfo.type == RobotType.POLITICIAN && robotInfo.team == Cache.OUR_TEAM) {
                    double currentTotalDamage = Math.max(0, robotInfo.conviction - 10);
                    //NOTE -> this method below gets the robot itself as well, so subtract 1
                    int currentUnitsNear = controller.detectNearbyRobots(robotInfo.location, 1).length - 1;
                    int damagePerUnit = (int) ((currentTotalDamage / currentUnitsNear) * empowerFactor);
                    minDamage += damagePerUnit;
                    maxDamage += (currentTotalDamage * empowerFactor);
                }
            }
        }

        int averageDamage = (minDamage + maxDamage) / 2;

        RobotInfo enemyECInfo = controller.senseRobotAtLocation(Cache.EC_INFO_LOCATION);

        Debug.printInformation("minDamage: " + minDamage + ", maxDamage: " + maxDamage + ", average: " + averageDamage, enemyECInfo.conviction);

        boolean toEmpower = false;
        if (enemyECInfo.team == Cache.OUR_TEAM) {
            if (enemyECInfo.influence <= 50) {
                if (myUnits <= 2) {
                    toEmpower = true;
                }
            }
        }

        if (averageDamage >= enemyECInfo.conviction && enemyECInfo.team != Cache.OUR_TEAM) {
            toEmpower = true;
        }

        //EXPLODE!!!?
        if (toEmpower) {
            if (controller.canEmpower(1)) {
                controller.empower(1);
                return true;
            }
        }

        return false;
    }

    public boolean leaveSpot() throws GameActionException {
        //TODO: check in sensor range if there is a different politician who is not next to the EC if they have more damage than I do, and if so, forfeit spot.

        boolean leaveSpot = false;

        MapLocation strongestRobot = null;

        for (RobotInfo robotInfo : Cache.ALL_NEARBY_FRIENDLY_ROBOTS) {
            if (robotInfo.type == RobotType.POLITICIAN && robotInfo.team == Cache.OUR_TEAM && robotInfo.conviction > controller.getConviction()) {
                if (robotInfo.location.distanceSquaredTo(Cache.EC_INFO_LOCATION) > 1) {
                    strongestRobot = robotInfo.location;
                    leaveSpot = true;
                    break;
                }
            }
        }

        // only leave if I am the weakest bot & it's in range of the strongest robot <-- chose this algo

        if (leaveSpot && strongestRobot != null) {
            // find weakest robot (or null space) among the 4 spots
            for (Direction direction : Constants.CARDINAL_DIRECTIONS) {
                MapLocation candidateLocation = Cache.EC_INFO_LOCATION.add(direction);

                if (controller.canSenseLocation(candidateLocation)) {
                    //exists
                    RobotInfo candidateRobot = controller.senseRobotAtLocation(candidateLocation);
                    int distanceCandidateToStrongest = candidateLocation.distanceSquaredTo(candidateLocation);

                    if (distanceCandidateToStrongest <= RobotType.POLITICIAN.sensorRadiusSquared) {

                        /* If there is an open spot or a weaker robot who is within range of the strongestRobot, let them move instead.
                        In event of tie in health, I think this algorithm should still work just as well */
                        if (candidateRobot == null || candidateRobot.conviction < controller.getConviction()) {
                            leaveSpot = false;
                            break;
                        }
                    }
                }

            }
        }

        Debug.printInformation("leaveSpot: " + leaveSpot, strongestRobot);


        if (leaveSpot) {
            Direction directionToMoveIn = Pathfinding.randomValidDirection();
            Debug.printInformation("Direction to move in: " + directionToMoveIn, " LEAVE PRIME SPOT ");
            if (directionToMoveIn != null && controller.canMove(directionToMoveIn)) {
                controller.move(directionToMoveIn);
                return true;
            }
        }

        return false;
    }


    public boolean moveToEmptySpot() throws GameActionException {
        //TODO: check if any of the 4 spots are empty, and if so,
        // go towards it if and only if there is no better politician health-wise who can take it
        // (if tie take closest to attacking location by travelDistance, then squaredDistance, then ID)

        //TODO: bug -- if they are all full by enemy units, do we return false? <-- I think so
        //TODO: bug -- make sure to still leave a gap of 3 from EC and me

        MapLocation closestSquare = null;
        int distance = 9999999;

        for (Direction direction : Constants.CARDINAL_DIRECTIONS) {
            MapLocation candidateLocation = Cache.EC_INFO_LOCATION.add(direction);
            if (controller.canSenseLocation(candidateLocation) && controller.senseRobotAtLocation(candidateLocation) == null) {
                int travelDistance = Pathfinding.travelDistance(Cache.CURRENT_LOCATION, candidateLocation);
                if (travelDistance < distance) {
                    closestSquare = candidateLocation;
                    distance = travelDistance;
                }
            }
        }

        /* MAYBE INSTEAD OF CACHE.ALL_NEARBY_FRIENDLY_ROBOTS  WE DO ALL POLITICIAN SENSE RADIUS CENTERED AROUND CLOSESTSQUARE */
        boolean moveTowardsEmptySpot = true;
        if (closestSquare != null) {
            // iterate through all politicians
            int myTravelDistance = Pathfinding.travelDistance(Cache.CURRENT_LOCATION, closestSquare);

            for (RobotInfo robotInfo : Cache.ALL_NEARBY_FRIENDLY_ROBOTS) {
                if (robotInfo.type == RobotType.POLITICIAN && robotInfo.team == Cache.OUR_TEAM) {
                    if (robotInfo.location.distanceSquaredTo(Cache.EC_INFO_LOCATION) > 1) {

                        /* A bot not on the 4 desired squares that is stronger than me -> other bot is better than me */
                        if (robotInfo.conviction > controller.getConviction()) {
                            moveTowardsEmptySpot = false;
                            break;
                        }

                        if (robotInfo.conviction == controller.getConviction()) {
                            int candidateTravelDistance = Pathfinding.travelDistance(robotInfo.location, closestSquare);

                            /* A bot with the same health who is closer than me -> other bot is better than me */
                            if (myTravelDistance > candidateTravelDistance) {
                                moveTowardsEmptySpot = false;
                                break;
                            }

                            /* A bot with the same health and travel distance who has a higher ID than me -> other bot is better than me */
                            if (myTravelDistance == candidateTravelDistance && robotInfo.ID > Cache.ID) {
                                moveTowardsEmptySpot = false;
                                break;
                            }
                        }
                    }
                }
            }
        }

        Debug.printInformation("closestSquare: " + closestSquare + ", moveTowardsEmptySpot: " + moveTowardsEmptySpot, " MOVE TO PRIME ATTACK SPOT ");

        if (closestSquare != null && moveTowardsEmptySpot) {
            //TODO (IMP): add some stuck parameter in case the closestSquare is inaccessible that returns false
            //TODO: move() is bugged sometimes where we go between the two same places?
            Pathfinding.move(closestSquare);
            return true;
        }

        return false;
    }

    private static int[] empowerValues = {1, 2, 4, 5, 8, 9};
    public void updateExplosionScores(int thresholdDecrease) {

        bestScore = -1;

        scoreThreshold = 1 + (controller.getConviction() - 10) * 0.2;
        scoreThreshold = scoreThreshold - Math.max(0, thresholdDecrease);

        if (thresholdDecrease == -999) scoreThreshold = 1 + (controller.getConviction() - 10) * 0.5;

        //TODO: if score has reached negative, then just go defend or something
        if (scoreThreshold < 1) {
            defendType = true;
            return;
        }

        bestExplosionRadius = -1;

        int bytecode = Clock.getBytecodeNum();

        for (int i = 0; i < empowerValues.length; ++i) {
            double currentScore = calculateScore(empowerValues[i]);
            if (currentScore > bestScore) {
                bestScore = currentScore;
                bestExplosionRadius = empowerValues[i];
            }
        }

        Debug.printByteCode("attackECLocation() BYTECODE USED: " + (Clock.getBytecodeNum() - bytecode));
        Debug.printInformation("[bestScore: " + bestScore + ", bestExplosionRadius: " + bestExplosionRadius + ", scoreThreshold: " + scoreThreshold + "]", " BEST EXPLOSION ");

        //TODO: IF exploding at this distance means my distance==1 polis can capture better, do it also

    }

    public boolean circleEnemyEC() throws GameActionException {
        //TODO: circle around the EC as close as possible but >= 5 distance. Use scoring function to determine if to explode

        /* MOVE IN CIRCLE MOTION */
        Direction preferredDirection = null;
        int preferredDistance = 9999999;

        if (Cache.CURRENT_LOCATION.distanceSquaredTo(Cache.EC_INFO_LOCATION) < 5) {
            // move away
            Direction awayECDir = Pathfinding.toMovePreferredDirection(Cache.EC_INFO_LOCATION.directionTo(Cache.CURRENT_LOCATION), 1);
            if (awayECDir != null) {
                Debug.printInformation("circling too close: " + awayECDir, " LEAVING EC RANGE ");
                controller.move(awayECDir);
            }
        }

        Direction towardsECDir = Cache.CURRENT_LOCATION.directionTo(Cache.EC_INFO_LOCATION);

        if (controller.canMove(towardsECDir)) {
            MapLocation candidateLocation = Cache.CURRENT_LOCATION.add(towardsECDir);
            int candidateDistance = candidateLocation.distanceSquaredTo(Cache.EC_INFO_LOCATION);
            if (candidateDistance >= 5) {
                preferredDirection = towardsECDir;
                preferredDistance = candidateDistance;
            }
        }

        if (circleDirectionClockwise) {
            towardsECDir = towardsECDir.rotateRight();
            MapLocation candidateLocation = Cache.CURRENT_LOCATION.add(towardsECDir);
            int candidateDistance = candidateLocation.distanceSquaredTo(Cache.EC_INFO_LOCATION);
            if (controller.canMove(towardsECDir) && candidateDistance >= 5 && candidateDistance <= preferredDistance) {
                preferredDirection = towardsECDir;
                preferredDistance = candidateDistance;
            }

            towardsECDir = towardsECDir.rotateRight();
            candidateLocation = Cache.CURRENT_LOCATION.add(towardsECDir);
            candidateDistance = candidateLocation.distanceSquaredTo(Cache.EC_INFO_LOCATION);
            if (controller.canMove(towardsECDir) && candidateDistance >= 5 && candidateDistance <= preferredDistance) {
                preferredDirection = towardsECDir;
                preferredDistance = candidateDistance;
            }
        } else {
            towardsECDir = towardsECDir.rotateLeft();
            MapLocation candidateLocation = Cache.CURRENT_LOCATION.add(towardsECDir);
            int candidateDistance = candidateLocation.distanceSquaredTo(Cache.EC_INFO_LOCATION);
            if (controller.canMove(towardsECDir) && candidateDistance >= 5 && candidateDistance <= preferredDistance) {
                preferredDirection = towardsECDir;
                preferredDistance = candidateDistance;
            }

            towardsECDir = towardsECDir.rotateLeft();
            candidateLocation = Cache.CURRENT_LOCATION.add(towardsECDir);
            candidateDistance = candidateLocation.distanceSquaredTo(Cache.EC_INFO_LOCATION);
            if (controller.canMove(towardsECDir) && candidateDistance >= 5 && candidateDistance <= preferredDistance) {
                preferredDirection = towardsECDir;
                preferredDistance = candidateDistance;
            }
        }

        Debug.printInformation("preferredDirection: " + preferredDirection + ", preferredDistance: " + preferredDistance + " circleDirectionClockwise: " + circleDirectionClockwise, " STAY IN CIRCLE RADIUS ");

        if (preferredDirection != null) {
            controller.move(preferredDirection);
            return true;
        } else {
            circleDirectionClockwise = !circleDirectionClockwise;
        }

        return false;
    }

    /* ATTACKING CODE 1/24 */



    // set flag if muckraker and slanderer in range
    public void setFlagToIndicateDangerToSlanderer() throws GameActionException {

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

        if (locationToWarnAbout != null && slanderSize > 0) { //If I have both a muckraker and slanderer in range of me, alert danger by inDanger to slanderers!
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
                // NOTE: NOT SETTING "Comms.hasSetFlag = true" IS BY FUNCTION (not a bug). We want to change the flag only to change its state from the previous one. If it is set to a different state/overridden, that is OKAY
            }
        }
    }




}
