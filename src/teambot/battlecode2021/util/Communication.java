package teambot.battlecode2021.util;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.Team;
import com.sun.tools.internal.jxc.ap.Const;

/*
COMMUNICATION SCHEMA
24 bits

*/

public class Communication {

    private static int SEED = 2193121;
    public static RobotController controller;

    public static void init(RobotController controller) {
        Communication.controller = controller;
    }

    /* SCHEMA 1: 0b1 | 3 extraType | 3 locationType | 3 extraData | 7 locationDataX | 7 locationDataY */
    public static int encode_ExtraANDLocationType_and_ExtraANDLocationData(
            Constants.FLAG_EXTRA_TYPES extraType, Constants.FLAG_LOCATION_TYPES locationType, int extraData, MapLocation locationData) {

        int flag = Constants.EXTRA_LOCATION_SCHEMA +
                encode_LocationType_and_LocationData(locationType, locationData) +
                encode_ExtraType_and_ExtraData(extraType, extraData);
        Debug.printInformation("encode_ExtraANDLocationType_and_ExtraANDLocationData() FINAL flag ", flag);
        return flag;
    }

    /* SCHEMA 2: 0b | 7 bits schema code (must start with 0) | 3 bits type of bot | 1 danger bit | 1 bit reset | 8 bits hot-one encoding | 4 bits information */
    public static int encode_MovementBotType_and_MovementBotData(Constants.MOVEMENT_BOTS_TYPES movementBotType, boolean isResetBitOn, boolean inDanger, int wallHoleData, Constants.MOVEMENT_BOTS_DATA movementBotData) {
        int flag = (Constants.MOVEMENT_BOT_SCHEMA_CODE << Constants.MOVEMENT_BOT_SCHEMA_SHIFT) +
                (movementBotType.ordinal() << Constants.MOVEMENT_BOT_TYPE_SHIFT) +
                (wallHoleData << Constants.MOVEMENT_BOT_WALLHOLE_SHIFT) +
                movementBotData.ordinal();
        if (inDanger) flag += (1 << Constants.MOVEMENT_BOT_DANGER_SHIFT);
        if (isResetBitOn) flag += (1 << Constants.MOVEMENT_BOT_RESET_SHIFT);

        Debug.printInformation("encode_MovementBotType_and_MovementBotData() FINAL flag ", flag);
        return flag;
    }

    /* SCHEMA 3: 0b | 3 bits schema code (011) | 2 bits locationType (one of the first 4 items) | 1 bit X isPositive | 4 bits X (compress by x4) | 1 bit Y isPositive | 4 bits Y (compress by x4) | 9 bits data (value * 50 = conviction) */
    public static int encode_LocationType_and_RelativeLocationDataANDHealthData(Constants.FLAG_LOCATION_TYPES locationType, MapLocation locationData, int conviction) {
        int flag = (Constants.RELATIVE_LOCATION_SCHEMA_CODE << Constants.RELATIVE_LOCATION_SCHEMA_SHIFT) +
                (locationType.ordinal() << Constants.RELATIIVE_LOCATION_IDENTIFIER_SHIFT);

        //relativeLocationData should be vector from start (EC) position to current (end) position
        int deltaX = locationData.x - Cache.myECLocation.x;
        int deltaY = locationData.y - Cache.myECLocation.y;
        int healthData = (conviction + 49) / 50; //round up
        healthData = Math.max(healthData, 511); //2^9-1 is max for bits, which represents

        flag += (((((deltaX < 0) ? 1 : 0) << 4) + (Math.abs(deltaX) >> 2)) << 14);
        flag += (((((deltaY < 0) ? 1 : 0) << 4) + (Math.abs(deltaY) >> 2)) << 9);
        flag += healthData;
        return flag;
    }

    public static boolean decodeIsFlagRelativeLocationType(int encoding) {
        boolean valid = (encoding >> Constants.RELATIVE_LOCATION_SCHEMA_SHIFT) == Constants.RELATIVE_LOCATION_SCHEMA_CODE;
        return valid;
    }

    /* return the type (meaning) of the location that was encoded. Assumes decodeIsFlagRelativeLocationType() is called first */
    public static Constants.FLAG_LOCATION_TYPES decodeRelativeLocationType(int encoding) {
        // ENUM_TYPE of index ((encoding >> 21) & 0b111)
        int identifier = (encoding >> Constants.RELATIVE_LOCATION_SCHEMA_SHIFT) & 0b11;
        Constants.FLAG_LOCATION_TYPES locationType = Constants.FLAG_LOCATION_TYPES.values()[identifier];
//        Debug.printInformation("decodeLocationType() ", locationType);
        return locationType;
    }

    public static MapLocation decodeRelativeLocationData(int encoding) {
        MapLocation locationData = Cache.myECLocation; //risky in edge-case situations with 2 ECs nearby

        int deltaX = ((((encoding >> 14) & 0b1111) << 2) + 2);
        if (((encoding >> 18) & 1) == 1) deltaX *= -1;

        int deltaY = ((((encoding >> 9) & 0b1111) << 2) + 2);
        if (((encoding >> 13) & 1) == 1) deltaY *= -1;

        return (locationData.translate(deltaX, deltaY));
    }

    public static int decodeRelativeHealthData(int encoding) {
        return (encoding & 0b111111111) * 50;
    }

    public static boolean decodeIsFlagMovementBotType(int encoding) {
        boolean valid = (encoding >> Constants.MOVEMENT_BOT_SCHEMA_SHIFT) == Constants.MOVEMENT_BOT_SCHEMA_CODE;
//        Debug.printInformation("decodeIsFlagMovementBotType() ", valid);
        return valid;
    }

    public static boolean decodeMovementBotIsDanger(int encoding) {
        return ((encoding >> Constants.MOVEMENT_BOT_DANGER_SHIFT) & 0b1) == 1;
    }

    public static boolean decodeMovementBotIsResetBitOn(int encoding) {
        return ((encoding >> Constants.MOVEMENT_BOT_RESET_SHIFT) & 0b1) == 1;
    }

    public static int decodeMovementBotWallHoleData(int encoding) {
        return ((encoding >> Constants.MOVEMENT_BOT_WALLHOLE_SHIFT) & 0b11111111);
    }

    public static Constants.MOVEMENT_BOTS_TYPES decodeMovementBotType(int encoding) {
        int identifier = ((encoding >> Constants.MOVEMENT_BOT_TYPE_SHIFT) & 0b111);
        Constants.MOVEMENT_BOTS_TYPES movementBotType = Constants.MOVEMENT_BOTS_TYPES.values()[identifier];
//        Debug.printInformation("decodeMovementBotType() ", movementBotType);
        return movementBotType;
    }

    public static Constants.MOVEMENT_BOTS_DATA convert_DirectionInt_MovementBotsData(int dir) {
        return Constants.MOVEMENT_BOTS_DATA.values()[dir + 1];
    }

    public static int convert_MovementBotData_DirectionInt(Constants.MOVEMENT_BOTS_DATA movementBotsData) {
        return (movementBotsData.ordinal() - 1);
    }

    public static Constants.MOVEMENT_BOTS_DATA decodeMovementBotData(int encoding) {
        int identifier = (encoding & Constants.MOVEMENT_BOTS_DATA_BITMASK);
        Constants.MOVEMENT_BOTS_DATA movementBotData = Constants.MOVEMENT_BOTS_DATA.values()[identifier];
//        Debug.printInformation("decodeMovementBotData() ", movementBotData);
        return movementBotData;
    }

    private static int encode_LocationType_and_LocationData(Constants.FLAG_LOCATION_TYPES locationType, MapLocation locationData) {
        int flag = encodeLocationType(locationType) + encodeLocationData(locationData);
//        Debug.printInformation("encode_LocationType_and_LocationData() flag ", flag);
        return flag;
    }

    /* CONVENTION IS -> 0b1 | 000 (not location based) | 3 location type | ...*/
    /* Uses bits 17-19 */
    private static int encodeLocationType(Constants.FLAG_LOCATION_TYPES locationType) {
        int encoding = (locationType.ordinal() << Constants.LOCATION_IDENTIFIER_SHIFT);
//        Debug.printInformation("encodeLocationType() flag ", encoding);
        return encoding;
    }

    /* Uses bits 0-13 inclusive */
    private static int encodeLocationData(MapLocation locationData) {
        int encoding = ((locationData.x & Constants.LOCATION_DATA_BITMASK) << Constants.LOCATION_DATA_NBITS) +
                (locationData.y & Constants.LOCATION_DATA_BITMASK);
//        Debug.printInformation("encodeLocationData() flag ", encoding);
        return encoding;
    }

    // ensure data <= 3 bits
    private static int encode_ExtraType_and_ExtraData(Constants.FLAG_EXTRA_TYPES extraType, int extraData) {
        int flag = encodeExtraType(extraType) + encodeExtraData(extraData);
//        Debug.printInformation("encode_ExtraType_and_ExtraData() flag ", flag);
        return flag;
    }

    private static int encodeExtraType(Constants.FLAG_EXTRA_TYPES extraType) {
        int encoding = (extraType.ordinal() << Constants.EXTRA_IDENTIFIER_SHIFT);
//        Debug.printInformation("encodeExtraType() flag ", encoding);
        return encoding;
    }

    private static int encodeExtraData(int extraData) {
        int encoding = (extraData & Constants.EXTRA_DATA_BITMASK) << Constants.EXTRA_DATA_SHIFT;
//        Debug.printInformation("encodeExtraData() flag ", encoding);
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
        boolean valid = (encoding >> Constants.LOCATION_SCHEMA_BIT) == 1;
        if (valid && extraVerification) {
            valid &= (decodeExtraType(encoding) == Constants.FLAG_EXTRA_TYPES.VERIFICATION_ENSURANCE);
        }
//        Debug.printInformation("decodeIsFlagLocationType() ", valid);
        return valid;
    }

    public static Constants.FLAG_EXTRA_TYPES decodeExtraType(int encoding) {
        // ENUM_TYPE of index ((encoding >> 20) & 0b111)
        int identifier = (encoding >> Constants.EXTRA_IDENTIFIER_SHIFT) & Constants.EXTRA_IDENTIFIER_BITMASK;

        Constants.FLAG_EXTRA_TYPES extraType = Constants.FLAG_EXTRA_TYPES.NOOP;
        if (identifier < Constants.FLAG_EXTRA_TYPES.values().length) extraType = Constants.FLAG_EXTRA_TYPES.values()[identifier];

//        Debug.printInformation("decodeExtraType() ", extraType);
        return extraType;
    }

    /* return the type (meaning) of the location that was encoded. Assumes decodeIsFlagLocationType() is called first */
    public static Constants.FLAG_LOCATION_TYPES decodeLocationType(int encoding) {
        // ENUM_TYPE of index ((encoding >> 17) & 0b111)
        int identifier = (encoding >> Constants.LOCATION_IDENTIFIER_SHIFT) & Constants.LOCATION_IDENTIFIER_BITMASK;
        Constants.FLAG_LOCATION_TYPES locationType = Constants.FLAG_LOCATION_TYPES.values()[identifier];
//        Debug.printInformation("decodeLocationType() ", locationType);
        return locationType;
    }

    public static int decodeExtraData(int encoding) {
        int extraData = (encoding >> Constants.EXTRA_DATA_SHIFT) & Constants.EXTRA_DATA_BITMASK;
//        Debug.printInformation("decodeExtraData() ", extraData);
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
            hasSetFlag = true;
            return true;
        }
        return false;
    }

    public static boolean checkAndAddFlag(int flag) throws GameActionException {
//        flag ^= SEED; // basic encryption
        
        if (controller.canSetFlag(flag)) {
            communicationQueue.add(flag);
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


    /* Communication Queue System */

    private static Queue<Integer> communicationQueue = new LinkedList<Integer>();
    public static boolean hasSetFlag;

    public static void loop() throws GameActionException {
        if (!hasSetFlag && communicationQueue.size() > 0) {
            int flag = communicationQueue.poll();
            checkAndSetFlag(flag);
        }
    }


}
