package teambot3.battlecode2021.util;
import battlecode.common.*;

public class Scout {

    public static RobotController controller;
    private static int hasScoutedEnemyNums;
    private static int scoutCooldown;

    public static void init(RobotController controller) {
        Scout.controller = controller;
    }

    // Check edge of sense area for map edges
    public static void scoutMapEdges() throws GameActionException {
        MapLocation currentLocation = controller.getLocation();
        if (Cache.MAP_TOP == 0) {
            checkEdge(Direction.NORTH, currentLocation, Cache.SENSOR_RADIUS);
        }
        if (Cache.MAP_RIGHT == 0) {
            checkEdge(Direction.EAST,currentLocation, Cache.SENSOR_RADIUS);
        }
        if (Cache.MAP_LEFT == 0) {
            checkEdge(Direction.WEST,currentLocation, Cache.SENSOR_RADIUS);
        }
        if (Cache.MAP_BOTTOM == 0) {
            checkEdge(Direction.SOUTH,currentLocation, Cache.SENSOR_RADIUS);
        }  
        
    }

    public static void scoutEnemies() throws GameActionException {
        int flag = 0;
        if (!Comms.hasSetFlag && Comms.getCommsSize() == 0 && controller.getFlag(controller.getID()) != 0) {
            controller.setFlag(flag);
        }
        if (hasScoutedEnemyNums >= 3) return;
        if (scoutCooldown > 0) {
            --scoutCooldown;
            return;
        }
        MapLocation currentLocation = controller.getLocation();
        int distance = Pathfinding.travelDistance(currentLocation, Cache.myECLocation);
        int numEnemies = controller.senseNearbyRobots(-1, Cache.OPPONENT_TEAM).length;
        int cost = (int)((1 + (15.0/distance)) * numEnemies);
        if (numEnemies > 0) {
            int dangerDirection = Cache.myECLocation.directionTo(currentLocation).ordinal();
            flag = CommunicationMovement.encodeMovement(true, true,
                    CommunicationMovement.MY_UNIT_TYPE.MU, CommunicationMovement.convert_DirectionInt_MovementBotsData(dangerDirection),
                    CommunicationMovement.COMMUNICATION_TO_OTHER_BOTS.SPOTTED_ENEMY_UNIT, false, false, cost);
            Comms.checkAndAddFlag(flag);
            hasScoutedEnemyNums++;
            scoutCooldown = 10;
        }
    }

    // Checks edge. If not on map, set cache and send flag
    // Changed to one function instead of four
    private static void checkEdge(Direction dir, MapLocation center, int radius) throws GameActionException {

        // added switch so we do not communicate the same message and backlog queue.
        switch(dir) {
            case NORTH:
                if (Cache.MAP_TOP != 0) return;
                break;
            case SOUTH:
                if (Cache.MAP_BOTTOM != 0) return;
                break;
            case EAST:
                if (Cache.MAP_RIGHT != 0) return;
                break;
            case WEST:
                if (Cache.MAP_LEFT != 0) return;
                break;
        }

        int x = dir.getDeltaX();
        int y = dir.getDeltaY();
        if (controller.onTheMap(center.translate(x*radius, y*radius))) return; //the furthest distance is already on the map (so the remaining must also be)
        int flag;
        for (int i = radius - 1; i != 0; --i) {
            MapLocation scanLocation = center.translate(x*i, y*i);
            if (controller.onTheMap(scanLocation)) {
                if (dir == Direction.NORTH) {
                    Cache.MAP_TOP=scanLocation.y;
                    flag = CommunicationLocation.encodeLOCATION(false, true, CommunicationLocation.FLAG_LOCATION_TYPES.NORTH_MAP_LOCATION, scanLocation);
                }
                else if (dir == Direction.SOUTH) {
                    Cache.MAP_BOTTOM=scanLocation.y;
                    flag = CommunicationLocation.encodeLOCATION(false, true, CommunicationLocation.FLAG_LOCATION_TYPES.SOUTH_MAP_LOCATION, scanLocation);
                }
                else if (dir == Direction.EAST) {
                    Cache.MAP_RIGHT=scanLocation.x;
                    flag = CommunicationLocation.encodeLOCATION(false, true, CommunicationLocation.FLAG_LOCATION_TYPES.EAST_MAP_LOCATION, scanLocation);
                }
                else {
                    Cache.MAP_LEFT=scanLocation.x;
                    flag = CommunicationLocation.encodeLOCATION(false, true, CommunicationLocation.FLAG_LOCATION_TYPES.WEST_MAP_LOCATION, scanLocation);
                }
                Comms.checkAndAddFlag(flag);
                return;
            }
        }
    }

    //TODO: Not sure if hashmap best data structure. Bytecode inefficient - change to array?
    public static void scoutECs() throws GameActionException {
        RobotInfo[] nearbyRobots = controller.senseNearbyRobots();
        for (RobotInfo info : nearbyRobots) { 
            if (info.type == RobotType.ENLIGHTENMENT_CENTER) {

                if (Cache.OUR_TEAM == info.team) { // COMMUNICATE EC location + ID iff I own the EC

                    if (!controller.canGetFlag(Cache.myECID)) {
                        Cache.myECLocation = info.location;
                        Cache.myECID = info.ID;
                    }

                    if (Cache.FOUND_ECS.getOrDefault(info.location, null) != getECType(info.team)) {
                        int flag = CommunicationLocation.encodeLOCATION(false, false, CommunicationLocation.FLAG_LOCATION_TYPES.MY_EC_LOCATION, info.location);
                        Comms.checkAndAddFlag(flag);
                        flag = CommunicationRobotID.encodeRobotID(false, true, CommunicationRobotID.COMMUNICATION_UNIT_TYPE.EC, CommunicationRobotID.COMMUNICATION_UNIT_TEAM.MY, info.ID);
                        Comms.checkAndAddFlag(flag);
                        Debug.printInformation("OUR EC FOUND " + info.location, "VALID");
                    }

                } else { //Otherwise communicate the team (enemy or neutral), health, and location.
                    boolean isNeutralTeam = true;
                    if (Cache.OPPONENT_TEAM == info.team) isNeutralTeam = false;

                    boolean moveAwayFromMe = false; // If I am a politician close to the EC I want to attack, then set my flag moveAwayFromMe and send with high priority
                    if (Cache.ROBOT_TYPE.equals(RobotType.POLITICIAN)
                            && Cache.EC_INFO_ACTION == CommunicationECSpawnFlag.ACTION.ATTACK_LOCATION
                            && Cache.CURRENT_LOCATION.distanceSquaredTo(Cache.EC_INFO_LOCATION) <= RobotType.POLITICIAN.sensorRadiusSquared) {
                        moveAwayFromMe = true;
                    }

                    Debug.printInformation("OTHER EC FOUND", "VALID");

                    int flag = CommunicationECDataSmall.encodeECHealthLocation(moveAwayFromMe, isNeutralTeam, info.conviction, info.location);
                    if (moveAwayFromMe && !Comms.hasSetFlag && controller.canSetFlag(flag)) {
                        controller.setFlag(flag);
                        Debug.printInformation("SETTING FLAG WITH HIGH PRIO FOR ATTACKING POLI -- GET AWAY FROM ME ", flag);
                        Comms.hasSetFlag = true;
                    } else {
                        Comms.checkAndAddFlag(flag);
                    }
                }

                Cache.FOUND_ECS.put(info.location, getECType(info.team));
            }
        }
    }
    
    /* Converts the EC Team to a Location Flag Type for communication purposes */
    private static CommunicationLocation.FLAG_LOCATION_TYPES getECType(Team ECTeam) {
        if (ECTeam.equals(Cache.OUR_TEAM)) {
            return CommunicationLocation.FLAG_LOCATION_TYPES.MY_EC_LOCATION;
        } else if (ECTeam.equals(Cache.OPPONENT_TEAM)) {
            return CommunicationLocation.FLAG_LOCATION_TYPES.ENEMY_EC_LOCATION;
        } else {
            return CommunicationLocation.FLAG_LOCATION_TYPES.NEUTRAL_EC_LOCATION;
        }
    }

//    private static CommunicationHealth.COMMUNICATION_UNIT_TEAM getCommunicatedUnitTeamForECInfo(Team ECTeam) {
//        if (ECTeam.equals(Cache.OUR_TEAM)) {
//            return CommunicationHealth.COMMUNICATION_UNIT_TEAM.MY;
//        } else if (ECTeam.equals(Cache.OPPONENT_TEAM)) {
//            return CommunicationHealth.COMMUNICATION_UNIT_TEAM.ENEMY;
//        } else {
//            return CommunicationHealth.COMMUNICATION_UNIT_TEAM.NEUTRAL;
//        }
//    }
//
//    private static CommunicationRobotID.COMMUNICATION_UNIT_TEAM getCommunicatedUnitTeamForRobotID(Team ECTeam) {
//        if (ECTeam.equals(Cache.OUR_TEAM)) {
//            return CommunicationRobotID.COMMUNICATION_UNIT_TEAM.MY;
//        } else if (ECTeam.equals(Cache.OPPONENT_TEAM)) {
//            return CommunicationRobotID.COMMUNICATION_UNIT_TEAM.ENEMY;
//        } else {
//            return CommunicationRobotID.COMMUNICATION_UNIT_TEAM.NEUTRAL;
//        }
//    }
}
