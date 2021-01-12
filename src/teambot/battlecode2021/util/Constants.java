package teambot.battlecode2021.util;
import battlecode.common.*;

public class Constants {

    /* COMMUNCATION */

    public static final int SCHEMA_BIT = 23;
    public static final int LOCATION_SCHEMA = 1 << SCHEMA_BIT;

    // The values for a flag with location information -> 10 "free" bits | 7 X coord bits | 7 Y coord bits
    public static final int LOCATION_NBITS = 7;
    public static final int LOCATION_BITMASK = (1 << LOCATION_NBITS) - 1;

    public static final int LOCATION_IDENTIFIER_NBITS = 17;

    // MAYBE LET LOCATION FLAGS BE SCHEMA FORM -> 0b000(3 bits)
    /* CANNOT BE MORE THAN 8 OPTIONS (3 bits) */
    public static enum FLAG_LOCATION_TYPES {
        MY_EC_LOCATION,
        ENEMY_EC_LOCATION,
        RIGHT_EDGE_LOCATION,
        TOP_EDGE_LOCATION,
        LEFT_EDGE_LOCATION,
        BOTTOM_EDGE_LOCATION,
    }
}
