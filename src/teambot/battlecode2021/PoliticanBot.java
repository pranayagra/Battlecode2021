package teambot.battlecode2021;

import battlecode.common.*;
import com.sun.tools.internal.jxc.ap.Const;
import teambot.RunnableBot;
import teambot.battlecode2021.util.*;

import java.nio.file.Path;
import java.util.Random;

public class PoliticanBot implements RunnableBot {
    private RobotController controller;
    private static Random random;
    private Pathfinding pathfinding;

    private MapLocation[] muckrakerLocations;
    private int[] muckrakerDistances;
    private int friendlySlanderersSize;

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

    private int triedCloserCnt;
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

        slanderLocations = new MapLocation[50];

        if (controller.getConviction() <= HEALTH_DEFEND_UNIT) defendType = true;

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

                Debug.printInformation("SWITCH TO ATTACK?: " + switchToAttack, Cache.EC_INFO_LOCATION);
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
            if (Cache.EC_INFO_LOCATION != null && Cache.FOUND_ECS.get(Cache.EC_INFO_LOCATION) == null) {
                attackECProtocol();
//                moveAndDestroyEC();
            } else if (Cache.EC_INFO_LOCATION != null && Cache.FOUND_ECS.get(Cache.EC_INFO_LOCATION) != CommunicationLocation.FLAG_LOCATION_TYPES.MY_EC_LOCATION) {
                attackECProtocol();
//                moveAndDestroyEC();
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

        Debug.printByteCode("END TURN POLI BYTECODE CHECK? => ");
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
                Debug.printInformation("STUCK DEFENDER. MOVING TOWARDS " + targetWhenStuck, " VALID ");
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




    //poli attack
    // IF I THINK SLANDERER HERE, COMMUNICATE TO EC -->
    // IF I THINK GOOD SCORE TO EXPLODE --> ATTACK
    // OTHERWISE MOVE CLOSER TO EC?

    //SCORE function --> if killed EC=> number of units killed + EC damage / 10 + % of EC damage - poli-health

    // kills 2 units and 10 dmg (5%), kills 0 units and 50 dmg (25%), kills 3 units and 0 damage

    //IF >=50% health

    // my influence => attack (we want to attack pretty quickly)
    //

    //some score function based on : my conviction (maybe not), number of units killed (scaled with my conviction wasted or something), % and value EC damage (% / 5), captured EC (+1000)
    //for units: %of damage (decimal) + 2 (if killed)

    public double calculateScore(int empowerDistanceSquared) {

//        int[] numberOfUnits = new int[25];
        //int ECDistance = Cache.CURRENT_LOCATION.distanceSquaredTo(Cache.EC_INFO_LOCATION);

        int numUnits = 0;
        for (RobotInfo robotInfo : Cache.ALL_NEARBY_ROBOTS) {
            int distance = Cache.CURRENT_LOCATION.distanceSquaredTo(robotInfo.location);
//            ++numberOfUnits[distance];
            if (distance <= empowerDistanceSquared) {
                ++numUnits;
            }
        }

        if (numUnits == 0) return -9999;

        int damage = (int) ((Math.max(0, controller.getConviction() - 10) / numUnits) * controller.getEmpowerFactor(Cache.OUR_TEAM,0));

        if (damage == 0) return -9999;

        int unitsKilled = 0;
        int totalDamage = 0;
        int ECDamage = 0;
        double ECHealth = 9999999;
        boolean ECCaptured = false;

        for (RobotInfo robotInfo : Cache.ALL_NEARBY_ROBOTS) {

            if (robotInfo.team == Cache.OUR_TEAM) continue;

            int distance = Cache.CURRENT_LOCATION.distanceSquaredTo(robotInfo.location);
            if (distance <= empowerDistanceSquared) {
                if (robotInfo.type == RobotType.ENLIGHTENMENT_CENTER) {
                    ECHealth = robotInfo.conviction;
                    ECDamage = damage;
                    if (ECDamage >= ECHealth) ECCaptured = true;
                }

                if (robotInfo.conviction <= damage) {
                    ++unitsKilled;
                    Debug.printInformation("KILLING " + robotInfo.location, " KILLED ");
                }
                totalDamage += Math.min(robotInfo.conviction, damage);
            }
        }

        /*
        double score = unitsKilled * 2; //1 killed unit => +2 score (max realistically 12)
        score += ((ECDamage / ECHealth) * 20); //5% health => +1 score (max 20)
        score += ECDamage / 10; //10 health => +1 score (max 40)
        score += (ECCaptured ? 100000 : 0); //killed EC => +10000 score
        score += totalDamage / (controller.getConviction() - 10) * unitsKilled * 5; // damage_decimal * unitsKilled * 5

        int damagedWasted = (controller.getConviction() - 10) - totalDamage;

        if (unitsKilled == 0) {
            score += ECDamage / 5; //5 health => +1 score
        }
         */

        double score = unitsKilled * 2;
        score += ECDamage / 10.0;
        score += (ECCaptured ? 100000: 0);

        if (unitsKilled == 0) {
            score -= 1;
        }

        Debug.printInformation("EMPOWER DISTANCE " + empowerDistanceSquared + ": [unitsKilled: " + unitsKilled + ", totalDamage: " + totalDamage + ", ECDamage: " + ECDamage + ", ECCaptured: " + ECCaptured + "] => SCORE ", score);

        return score;
    }


    /* ATTACKING CODE 1/24 (more for enemy EC) */

    //TODO: bug --> unit thinks for 1 turn that EC is still enemies -- maybe idk, not a huge deal I think tho?

    public void attackECProtocol() throws GameActionException {
        int distanceFromECToAttack = Cache.CURRENT_LOCATION.distanceSquaredTo(Cache.EC_INFO_LOCATION);

        if (!controller.isReady()) return;

        if (distanceFromECToAttack == 1) {
            decreaseScoreThresholdAmount = 0;
            if (worthExplodingEC()) return; //TODO: we should wait but also not wait forever...
            if (leaveSpot()) return;
        } else if (distanceFromECToAttack <= 20) { //TODO: not sure if this number 20 is best value
            decreaseScoreThresholdAmount++;
            //TODO: first determine if exploding right now will capture EC -> if so do it
            if (moveToEmptySpot()) return; //TODO: not complete method yet (may get stuck for long time) -- do it based on process too
            if (explodeUselessRobots((decreaseScoreThresholdAmount - 3) / 2)) return;
            if (circleEnemyEC()) return;
        } else {
            //TODO: move closer. If we have not gained distance in ~20 rounds AND ~4 potential moves, then we should consider exploding & decreasing the threshold as time goes on.
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
            if (isStuck && explodeUselessRobots((numRoundsTaken - 20) / 4 )) return;

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
        //minDamage bugged?

        for (Direction direction : Constants.CARDINAL_DIRECTIONS) {
            MapLocation checkStrongPoliticianLocation = Cache.EC_INFO_LOCATION.add(direction);

            if (checkStrongPoliticianLocation.equals(Cache.CURRENT_LOCATION)) {
                int currentTotalDamage = Math.max(0, controller.getConviction() - 10);
                int currentUnitsNear = controller.senseNearbyRobots(1).length;
                int damagePerUnit = currentTotalDamage / currentUnitsNear;
                minDamage += damagePerUnit;
                maxDamage += damagePerUnit; //NOT a typo (maxDamage += damagePerUnit)
            } else if (controller.canSenseLocation(checkStrongPoliticianLocation)) {
                RobotInfo robotInfo = controller.senseRobotAtLocation(checkStrongPoliticianLocation);
                if (robotInfo != null && robotInfo.type == RobotType.POLITICIAN && robotInfo.team == Cache.OUR_TEAM) {
                    int currentTotalDamage = Math.max(0, robotInfo.conviction - 10);
                    int currentUnitsNear = controller.detectNearbyRobots(robotInfo.location, 1).length;
                    Debug.printInformation("CTD: " + currentTotalDamage, currentUnitsNear);
                    int damagePerUnit = currentTotalDamage / currentUnitsNear;
                    minDamage += damagePerUnit;
                    maxDamage += currentTotalDamage;
                }
            }
        }

        minDamage *= controller.getEmpowerFactor(Cache.OUR_TEAM, 0);
        maxDamage *= controller.getEmpowerFactor(Cache.OUR_TEAM, 0);


        int averageDamage = (minDamage + maxDamage) / 2;

        RobotInfo enemyECInfo = controller.senseRobotAtLocation(Cache.EC_INFO_LOCATION);

        Debug.printInformation("minDamage: " + minDamage + ", maxDamage: " + maxDamage + ", average: " + averageDamage, enemyECInfo.conviction);

        if (averageDamage >= enemyECInfo.conviction) {
            //EXPLODE!!!
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
        //only leave if I am the closest bot weakest bot

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

        //TODO: bug -- if they are all full by enemy units, do we return false?
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
            //TODO: extend distance mucks from politicians
            Pathfinding.move(closestSquare);
            return true;
        }

        return false;
    }

    private static int[] empowerValues = {1, 2, 4, 5, 8, 9};
    public boolean explodeUselessRobots(int thresholdDecrease) throws GameActionException {

        double scoreThreshold = 1 + (controller.getConviction() - 10) * 0.2;
        scoreThreshold = scoreThreshold - Math.max(0, thresholdDecrease);

        //TODO: if score has reached negative, then just go defend or something
        if (scoreThreshold < 1) {
            defendType = true;
            return false;
        }

        double bestScore = -1;
        int empowerValue = -1;

        int bytecode = Clock.getBytecodeNum();

        for (int i = 0; i < empowerValues.length; ++i) {
            double currentScore = calculateScore(empowerValues[i]);
            if (currentScore > bestScore) {
                bestScore = currentScore;
                empowerValue = empowerValues[i];
            }
        }

        Debug.printByteCode("attackECLocation() BYTECODE USED: " + (Clock.getBytecodeNum() - bytecode));
        Debug.printInformation("[bestScore: " + bestScore + ", empowerValue: " + empowerValue + ", scoreThreshold: " + scoreThreshold + "]", " BEST EXPLOSION ");

        if (bestScore >= scoreThreshold) {
            Debug.printInformation("EXPLODING WITH " + empowerValue + " SINCE (bestScore) " + bestScore + " >= (scoreThreshold) " + scoreThreshold, Clock.getBytecodesLeft());
            if (controller.canEmpower(empowerValue)) {
                controller.empower(empowerValue);
                return true;
            }
        }

        return false;
    }

    public boolean circleEnemyEC() throws GameActionException {
        //TODO: circle around the EC as close as possible but >= 5 distance. Use scoring function to determine if to explode

        /* MOVE IN CIRCLE MOTION */
        Direction preferredDirection = null;
        int preferredDistance = 9999999;

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

        Debug.printInformation("preferredDirection: " + towardsECDir + ", preferredDistance: " + preferredDirection + " circleDirectionClockwise: " + circleDirectionClockwise, " STAY IN CIRCLE RADIUS ");

        if (preferredDirection != null) {
            controller.move(preferredDirection);
            return true;
        } else {
            circleDirectionClockwise = !circleDirectionClockwise;
        }

        return false;
    }

    /* ATTACKING CODE 1/24 */


    public boolean attackECLocation() throws GameActionException {

        int distance = Cache.CURRENT_LOCATION.distanceSquaredTo(Cache.EC_INFO_LOCATION);

         // only consider exploding at a good time IF we are stuck

        //IF MY DISTANCE IS 1, I AM A PRIMARY EC ATTACK BOT
        //IF MY DISTANCE IS GREATER, I WANT TO CLEAR THE HERD
        //ESSENTIALLY => WE HAVE TWO VALUES => KILLING ENEMY DAMAGE WITH ANY RADIUS + IF DISTANCE=1, THEN EC ATTACK
        //  WE CALCULATE THE TOTAL EC ATTACK => AND DETERMINE IF IT'S WORTH
        //  EACH OTHER UNIT

        if (bestDistanceOnAttack > distance) {
            bestDistanceOnAttack = distance;
            numActionTurnsTaken = 0;
            numRoundsTaken = 0;
        } else {
            numRoundsTaken++;
            if (controller.isReady()) numActionTurnsTaken++;
        }

        boolean isStuck = false;
        if (numRoundsTaken >= 20 && numActionTurnsTaken >= 5) {
            isStuck = true;
        }

        if (!isStuck && distance >= 20) {
            pathfinding.move(Cache.EC_INFO_LOCATION);
            return true;
        }

        double scoreThreshold = 1 + (controller.getConviction() - 10) * 0.2;

        if (isStuck) {
            scoreThreshold = scoreThreshold - (numActionTurnsTaken - 20);
        }

        //If I am not stuck and >= 20 distance, NEVER explode. just move closer to target.
        //If I am not stuck and < 20 distance, you can explode if necessary

        //If I am stuck and >= 20 distance, begin limiting the requirement on explosion
        //If I am stuck and < 20 distance, begin limiting the requirement on explosion
        //If I am stuck, begin limiting the requirment on explosion

        //When I feel like I am stuck, we should limit the requirement on explosion -->

        double bestScore = -1;
        int empowerValue = -1;

        int bytecode = Clock.getBytecodeNum();

        for (int i = 0; i < empowerValues.length; ++i) {

            double currentScore = calculateScore(empowerValues[i]);
            if (currentScore > bestScore) {
                bestScore = currentScore;
                empowerValue = empowerValues[i];
            }
        }

        Debug.printByteCode("attackECLocation() BYTECODE USED: " + (Clock.getBytecodeNum() - bytecode));
        Debug.printInformation("[bestScore: " + bestScore + ", empowerValue: " + empowerValue + ", scoreThreshold: " + scoreThreshold + "]", " BEST EXPLOSION ");

        if (bestScore >= scoreThreshold) {
            Debug.printInformation("EXPLODING WITH " + empowerValue + " SINCE (bestScore) " + bestScore + " >= (scoreThreshold) " + scoreThreshold, Clock.getBytecodesLeft());
            if (controller.canEmpower(empowerValue)) {
                controller.empower(empowerValue);
                return true;
            }
        } else {
            if (distance > 1) {
                pathfinding.move(Cache.EC_INFO_LOCATION);
                return true;
            }
        }

        return false;
    }

    // floor(damage/#units) * buff -->

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
