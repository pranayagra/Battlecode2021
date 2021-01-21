package sprint.battlecode2021.util;

import battlecode.common.MapLocation;

public class CommunicationECDataSmall {

    public static final int FLAG_CODE = 0b110;
    public static final int LOCATION_DATA_NBITS = 7;
    public static final int LOCATION_DATA_BITMASK = (1 << LOCATION_DATA_NBITS) - 1;


    /* SCHEMA: 3 bits code | 1 bit moveAwayFromMe | 1 bit myTeam | 5 bits health (value * 100) | 14 bits locationdata */

    public static int encodeECHealthLocation(
            boolean isMoveAwayFromMe, boolean isMyTeam, int health, MapLocation locationData) {
        health = Math.min(3100, health);
        health = ((health + 99) / 100) & 0b11111;

        return (FLAG_CODE << 21) +
                ((isMoveAwayFromMe ? 1 : 0) << 20) +
                ((isMyTeam ? 1 : 0) << 19) +
                (health << 14) +
                encodeLocationData(locationData);
    }

    public static boolean decodeIsSchemaType(int encoding) {
        return (encoding >> 21) == FLAG_CODE;
    }

    public static boolean decodeIsMoveAwayFromMe(int encoding) {
        return ((encoding >> 20) & 1) == 1;
    }

    public static boolean decodeIsMyTeam(int encoding) {
        return ((encoding >> 19) & 1) == 1;
    }

    public static int decodeHealth(int encoding) {
        return ((encoding >> 14) & 0b11111) * 100;
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
