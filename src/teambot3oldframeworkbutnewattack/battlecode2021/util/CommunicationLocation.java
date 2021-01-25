package teambot3oldframeworkbutnewattack.battlecode2021.util;

import battlecode.common.MapLocation;
import battlecode.common.RobotType;

public class CommunicationLocation extends Comms {

    /* DONE */

    public static final int FLAG_CODE = 0b001;

    public static final int LOCATION_DATA_NBITS = 7;
    public static final int LOCATION_DATA_BITMASK = (1 << LOCATION_DATA_NBITS) - 1;

    /* 3 bits max */
    public static enum FLAG_LOCATION_TYPES {
        NOOP,
        MY_EC_LOCATION,
        ENEMY_EC_LOCATION,
        NEUTRAL_EC_LOCATION,
        NORTH_MAP_LOCATION,
        EAST_MAP_LOCATION,
        SOUTH_MAP_LOCATION,
        WEST_MAP_LOCATION,
    }

    /*2 bits max */
    public static enum MY_UNIT_TYPE {
        EC,
        MU,
        PO,
        SL,
    }

    private static MY_UNIT_TYPE getMyUnitType() {
        switch (Cache.ROBOT_TYPE) {
            case ENLIGHTENMENT_CENTER:
                return MY_UNIT_TYPE.EC;
            case MUCKRAKER:
                return MY_UNIT_TYPE.MU;
            case POLITICIAN:
                return MY_UNIT_TYPE.PO;
            default:
                return MY_UNIT_TYPE.SL;
        }
    }

    /* SCHEMA: 3 bits code | 1 bit skippedQueue | 1 bit last flag | 2 bits unit type (me) | 3 bits location type | 14 bits locationdata */
    public static int encodeLOCATION(
            boolean isUrgent, boolean isLastFlag, FLAG_LOCATION_TYPES locationType, MapLocation locationData) {

        return (FLAG_CODE << 21) +
                ((isUrgent ? 1 : 0) << 20) +
                ((isLastFlag ? 1 : 0) << 19) +
                (getMyUnitType().ordinal() << 17) +
                (locationType.ordinal() << 14) +
                encodeLocationData(locationData);

    }

    public static boolean decodeIsSchemaType(int encoding) {
       return (encoding >> 21) == FLAG_CODE;
    }

    public static MY_UNIT_TYPE decodeMyUnitType(int encoding) {
        return MY_UNIT_TYPE.values()[(encoding >> 17) & 0b11];
    }

    public static FLAG_LOCATION_TYPES decodeLocationType(int encoding) {
        return FLAG_LOCATION_TYPES.values()[(encoding >> 14) & 0b111];
    }

    /* Decode the flag which contains the actual location data. Assumes decodeIsFlagLocationType() is called first */
    public static MapLocation decodeLocationData(int encoding) {
        // Math: (currentLocationX / 128 * 128) + (bits 7-13 inclusive shifted to position 0-6, other bits removed)
        return new MapLocation(decodeLocationDataHelper((Cache.CURRENT_LOCATION.x & ~LOCATION_DATA_BITMASK) +
                ((encoding >> LOCATION_DATA_NBITS) & LOCATION_DATA_BITMASK), Cache.CURRENT_LOCATION.x), decodeLocationDataHelper((Cache.CURRENT_LOCATION.y & ~LOCATION_DATA_BITMASK) +
                (encoding & LOCATION_DATA_BITMASK), Cache.CURRENT_LOCATION.y));
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

    /* Uses bits 0-13 inclusive */
    private static int encodeLocationData(MapLocation locationData) {
        return ((locationData.x & LOCATION_DATA_BITMASK) << LOCATION_DATA_NBITS) +
                (locationData.y & LOCATION_DATA_BITMASK);
    }



}
