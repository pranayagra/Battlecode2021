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
    // Maybe the 3 extra bits could be something about the 3 types of units and what to do (0, 0) is go to location, (0, 1) is stay away from location, (1, 1) is ?
    // they could also be used as additional verification to ensure what we are sending is actually ours (good for important and risky information)

    //0b0 | SOME OTHER SCHEMA of 23 bits to convey other type of information (should include a verification check too)
    //0b0 | 4 bits identifer (0101) => ID-based
    //0b0 | 4 bits identifer (1111) =>
    //Politican Attacking EC ->

    /* SCHEMA: 0b1 | 3 extraType | 3 locationType | 3 extraData | 7 locationDataX | 7 locationDataY */
    public static int encode_ExtraANDLocationType_and_ExtraANDLocationData(
            Constants.FLAG_EXTRA_TYPES extraType, Constants.FLAG_LOCATION_TYPES locationType, int extraData, MapLocation locationData) {

        int flag = Constants.EXTRA_LOCATION_SCHEMA +
                encode_LocationType_and_LocationData(locationType, locationData) +
                encode_ExtraType_and_ExtraData(extraType, extraData);
        Debug.printInformation("encode_ExtraANDLocationType_and_ExtraANDLocationData() FINAL flag ", flag);
        return flag;
    }

    private static int encode_LocationType_and_LocationData(Constants.FLAG_LOCATION_TYPES locationType, MapLocation locationData) {
        int flag = encodeLocationType(locationType) + encodeLocationData(locationData);
        Debug.printInformation("encode_LocationType_and_LocationData() flag ", flag);
        return flag;
    }

    /* CONVENTION IS -> 0b1 | 000 (not location based) | 3 location type | ...*/
    /* Uses bits 17-19 */
    private static int encodeLocationType(Constants.FLAG_LOCATION_TYPES locationType) {
        int encoding = (locationType.ordinal() << Constants.LOCATION_IDENTIFIER_SHIFT);
        Debug.printInformation("encodeLocationType() flag ", encoding);
        return encoding;
    }

    /* Uses bits 0-13 inclusive */
    private static int encodeLocationData(MapLocation locationData) {
        int encoding = ((locationData.x & Constants.LOCATION_DATA_BITMASK) << Constants.LOCATION_DATA_NBITS) +
                (locationData.y & Constants.LOCATION_DATA_BITMASK);
        Debug.printInformation("encodeLocationData() flag ", encoding);
        return encoding;
    }

    // ensure data <= 3 bits
    private static int encode_ExtraType_and_ExtraData(Constants.FLAG_EXTRA_TYPES extraType, int extraData) {
        int flag = encodeExtraType(extraType) + encodeExtraData(extraData);
        Debug.printInformation("encode_ExtraType_and_ExtraData() flag ", flag);
        return flag;
    }

    private static int encodeExtraType(Constants.FLAG_EXTRA_TYPES extraType) {
        int encoding = (extraType.ordinal() << Constants.EXTRA_IDENTIFIER_SHIFT);
        Debug.printInformation("encodeExtraType() flag ", encoding);
        return encoding;
    }

    private static int encodeExtraData(int extraData) {
        int encoding = (extraData & Constants.EXTRA_DATA_BITMASK) << Constants.EXTRA_DATA_SHIFT;
        Debug.printInformation("encodeExtraData() flag ", encoding);
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

    /* Checks if the flag is of type location based on 23rd bit == 1
    * If extraVerification, make sure that some extra bits are also valid
    * */
    public static boolean decodeIsFlagLocationType(int encoding, boolean extraVerification) {
        boolean valid = (encoding >> Constants.SCHEMA_BIT) == 1;
        if (valid && extraVerification) {
            valid &= (decodeExtraType(encoding) == Constants.FLAG_EXTRA_TYPES.VERIFICATION_ENSURANCE);
        }
        Debug.printInformation("decodeIsFlagLocationType() ", valid);
        return valid;
    }

    public static Constants.FLAG_EXTRA_TYPES decodeExtraType(int encoding) {
        // ENUM_TYPE of index ((encoding >> 20) & 0b111)
        int identifier = (encoding >> Constants.EXTRA_IDENTIFIER_SHIFT) & Constants.EXTRA_IDENTIFIER_BITMASK;
        Constants.FLAG_EXTRA_TYPES extraType = Constants.FLAG_EXTRA_TYPES.values()[identifier];
        Debug.printInformation("decodeExtraType() ", extraType);
        return extraType;
    }

    /* return the type (meaning) of the location that was encoded. Assumes decodeIsFlagLocationType() is called first */
    public static Constants.FLAG_LOCATION_TYPES decodeLocationType(int encoding) {
        // ENUM_TYPE of index ((encoding >> 17) & 0b111)
        int identifier = (encoding >> Constants.LOCATION_IDENTIFIER_SHIFT) & Constants.LOCATION_IDENTIFIER_BITMASK;
        Constants.FLAG_LOCATION_TYPES locationType = Constants.FLAG_LOCATION_TYPES.values()[identifier];
        Debug.printInformation("decodeLocationType() ", locationType);
        return locationType;
    }

    public static int decodeExtraData(int encoding) {
        int extraData = (encoding >> Constants.EXTRA_DATA_SHIFT) & Constants.EXTRA_DATA_BITMASK;
        Debug.printInformation("decodeExtraData() ", extraData);
        return extraData;
    }

    /* Decode the flag which contains the actual location data. Assumes decodeIsFlagLocationType() is called first */
    public static MapLocation decodeLocationData(int encoding) {
        // x represents a proposed location shifted to the current location
        // Math: (currentLocationX / 128 * 128) + (bits 7-13 inclusive shifted to position 0-6, other bits removed)
        int x = (Cache.CURRENT_LOCATION.x & ~Constants.LOCATION_DATA_BITMASK) +
                ((encoding >> Constants.LOCATION_DATA_NBITS) & Constants.LOCATION_DATA_BITMASK);
        x = decodeLocationDataHelper(x, Cache.CURRENT_LOCATION.x);

        int y = (Cache.CURRENT_LOCATION.y & ~Constants.LOCATION_DATA_BITMASK) +
                (encoding & Constants.LOCATION_DATA_BITMASK);
        y = decodeLocationDataHelper(y, Cache.CURRENT_LOCATION.y);

        MapLocation locationData = new MapLocation(x, y);
        Debug.printInformation("decodeLocationData() ", locationData);
        return locationData;
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




    /* SCHEMA: 0b0 | 23 bits */


    //OLD CODE:
    public final static int POLITICIAN_ATTACK_FLAG = 0b010111111111111111111111;

    public static boolean isPoliticianAttackingFlag(int flag) {
        if (flag == POLITICIAN_ATTACK_FLAG) {
            return true;
        }
        return false;
    }

}
