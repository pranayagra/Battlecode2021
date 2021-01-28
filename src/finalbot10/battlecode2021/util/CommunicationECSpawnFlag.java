package finalbot10.battlecode2021.util;

import battlecode.common.Direction;
import battlecode.common.MapLocation;

public class CommunicationECSpawnFlag {

    /* DONE */

    public static final int FLAG_CODE = 0b101;

    public static final int LOCATION_DATA_NBITS = 7;
    public static final int LOCATION_DATA_BITMASK = (1 << LOCATION_DATA_NBITS) - 1;

    /* 2 bits max */
    public static enum ACTION {
        DEFEND_LOCATION,
        ATTACK_LOCATION,
        SCOUT_LOCATION,
        SLANDER_ROTATE,
    }

    /* 2 bits max */
    public static enum SAFE_QUADRANT {
        NORTH_EAST,
        SOUTH_EAST,
        SOUTH_WEST,
        NORTH_WEST
    }

    /* SCHEMA: 3 bits code | 3 bits spawnDirection | 2 bits ACTION | 2 bits slanderer safe quadrants | 14 bits location */
    // MAX robotHealth is 131071
    public static int encodeSpawnInfo(
            Direction direction, ACTION actionType, SAFE_QUADRANT safeQuadrant, MapLocation locationData) {
        return (FLAG_CODE << 21) +
                ((direction.ordinal() & 0b111) << 18) +
                (actionType.ordinal() << 16) +
                (safeQuadrant.ordinal() << 14) +
                encodeLocationData(locationData);
    }

    public static boolean decodeIsSchemaType(int encoding) {
        return (encoding >> 21) == FLAG_CODE;
    }

    public static Direction decodeDirection(int encoding) {
        return Direction.values()[(encoding >> 18) & 0b111];
    }

    public static ACTION decodeAction(int encoding) {
        return ACTION.values()[(encoding >> 16) & 0b11];
    }

    public static SAFE_QUADRANT decodeSafeQuadrant(int encoding) {
        return SAFE_QUADRANT.values()[(encoding >> 14) & 0b11];
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
