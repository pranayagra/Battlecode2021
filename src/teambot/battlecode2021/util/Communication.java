package teambot.battlecode2021.util;

import java.util.ArrayList;
import java.util.Map;

import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.Team;

/*
COMMUNICATION SCHEMA
24 bits

[0000]
Bit 0-3: Order identifier

[00000000000000000000]
Bit 
*/

public class Communication {

    private static int SEED = 2193121;
    public static RobotController controller;

    public static void init(RobotController controller) {
        Communication.controller = controller;
    }

    //0b1 | 3 bits A extra identifier | 3 bits B location identifier | 3 bits data of type A | 7 bits location X of type B | 7 bits location Y of type B
    // Maybe the 3 bits could be something about the 3 types of units and what to do (0, 0) is go to location, (0, 1) is stay away from location, (1, 1) is ?
    // they could also be used as additional verification to ensure what we are sending is actually ours (good for important and risky information)

    //0b0 | SOME OTHER SCHEMA of 23 bits to convey other type of information

    public static int encode_LocationType_and_LocationData(Constants.FLAG_LOCATION_TYPES locationType, MapLocation location) {
        int flag = encodeLocationType(locationType) + encodeLocationData(location);
        Debug.printInformation("encode_LocationType_and_LocationData() flag ", flag);
        return flag;
    }

    /* CONVENTION IS -> 0b1 | 000 (not location based) | 3 location type | ...*/
    /* Uses bits 17-19 & 23 */
    private static int encodeLocationType(Constants.FLAG_LOCATION_TYPES locationType) {
        int encoding = Constants.LOCATION_SCHEMA +
                (locationType.ordinal() << Constants.LOCATION_IDENTIFIER_NBITS);
        Debug.printInformation("encodeLocationType() flag ", encoding);
        return encoding;
    }

    /* Uses bits 0-13 inclusive */ //NEED SOME ORDER IDENTIFIER TO IDENTIFY WHAT LOCATION MEANS (ALLY EC, EDGE OF MAP, ENEMY EC, NEUTRAL EC, ETC) ?
    private static int encodeLocationData(MapLocation location) {
        int encoding = ((location.x & Constants.LOCATION_BITMASK) << Constants.LOCATION_NBITS) +
                (location.y & Constants.LOCATION_BITMASK);
        Debug.printInformation("encodeLocationData() flag ", encoding);
        return encoding;
    }


    /* Location helper given a coordinate and current coordinate */
    private static int decodeLocationDataHelper(int encodedCoordinate, int currentLocationCoordinate) {
        int absoluteDistance = Math.abs(encodedCoordinate - currentLocationCoordinate);

        if (absoluteDistance > Math.abs(128 + encodedCoordinate - currentLocationCoordinate)) {
            encodedCoordinate += 128;
        } else if (absoluteDistance > Math.abs(-128 + encodedCoordinate - currentLocationCoordinate)) {
            encodedCoordinate -= 128;
        }

        return encodedCoordinate;
    }

    /* Checks if the flag is of type location based on 23rd bit */
    //TODO: IMPLEMENT EXTRA VERIFICATION FOR CERTAIN MESSAGES
    public static boolean decodeIsFlagLocationType(int encoding, boolean extraVerification) {
        return (encoding >> Constants.SCHEMA_BIT) == 1;
    }

    /* return the meaning of the location */
    public static Constants.FLAG_LOCATION_TYPES decodeLocationType(int encoding) {
        int identifier = (encoding & (0b111 << Constants.LOCATION_IDENTIFIER_NBITS)) >> Constants.LOCATION_IDENTIFIER_NBITS;
        return Constants.FLAG_LOCATION_TYPES.values()[identifier];
    }

    /* Decode the flag which contains location information. Assumes encoding is valid location flag */
    public static MapLocation decodeLocationData(int encoding) {
        // x represents a proposed location shifted to the current location
        // Math: (currentLocationX / 128 * 128) + (bits 7-13 inclusive shifted to position 0-6)
        int x = (Cache.CURRENT_LOCATION.x & Constants.LOCATION_BITMASK) +
                ((encoding >> Constants.LOCATION_NBITS) & Constants.LOCATION_BITMASK);
        x = decodeLocationDataHelper(x, Cache.CURRENT_LOCATION.x);

        int y = (Cache.CURRENT_LOCATION.y & Constants.LOCATION_BITMASK) +
                (encoding & Constants.LOCATION_BITMASK);
        y = decodeLocationDataHelper(y, Cache.CURRENT_LOCATION.y);

        MapLocation decodedLocation = new MapLocation(x, y);
        Debug.printLocation(decodedLocation);
        return decodedLocation;
    }



    public static boolean checkAndSetFlag(int flag) throws GameActionException {
//        flag ^= SEED; // basic encryption

        if (controller.canSetFlag(flag)) {
            controller.setFlag(flag);
            return true;
        }
        return false;
    }

    public static int checkAndGetFlag(int robotID) throws GameActionException {
        int flag = -1;
        if (controller.canGetFlag(robotID)) {
            flag = controller.getFlag(robotID);
//            flag ^= SEED; //basic decryption
        }
        return flag;
    }



    //OLD CODE:
    public final static int POLITICIAN_ATTACK_FLAG = 0b010111111111111111111111;

    public static boolean isPoliticianAttackingFlag(int flag) {
        if (flag == POLITICIAN_ATTACK_FLAG) {
            return true;
        }
        return false;
    }

}
