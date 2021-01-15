package teambot.battlecode2021.util;
import battlecode.common.*;

public class Constants {

    /* COMMUNCATION */


    /* SCHEMA 1: 0b1 | 3 bits extraType | 3 bits locationType | 3 bits extraData | 14 bits location X+Y */
    public static final int LOCATION_SCHEMA_BIT = 23;
    public static final int EXTRA_LOCATION_SCHEMA = 1 << LOCATION_SCHEMA_BIT;


    // The values for a flag with location information -> 10 "free" bits | 7 X coord bits | 7 Y coord bits
    public static final int LOCATION_DATA_NBITS = 7;
    public static final int LOCATION_DATA_BITMASK = (1 << LOCATION_DATA_NBITS) - 1;
    public static final int LOCATION_IDENTIFIER_SHIFT = 17;
    public static final int LOCATION_IDENTIFIER_NBITS = 3;
    public static final int LOCATION_IDENTIFIER_BITMASK = (1 << LOCATION_IDENTIFIER_NBITS) - 1;

    public static final int EXTRA_DATA_SHIFT = 14;
    public static final int EXTRA_DATA_NBITS = 3;
    public static final int EXTRA_DATA_BITMASK = (1 << EXTRA_DATA_NBITS) - 1;
    public static final int EXTRA_IDENTIFIER_SHIFT = 20;
    public static final int EXTRA_IDENTIFIER_NBITS = 3;
    public static final int EXTRA_IDENTIFIER_BITMASK = (1 << EXTRA_DATA_NBITS) - 1;

    // MAYBE LET LOCATION FLAGS BE SCHEMA FORM -> 0b1|???|3 bits LOCATION FLAGS set here|...
    /* CANNOT BE MORE THAN 8 OPTIONS (3 bits) */
    public static enum FLAG_LOCATION_TYPES {
        NOOP,
        MY_EC_LOCATION,
        ENEMY_EC_LOCATION,
        NEUTRAL_EC_LOCATION,
        LEFT_OR_RIGHT_MAP_LOCATION,
        TOP_OR_BOTTOM_MAP_LOCATION
    }

    // TODO: ADD MORE STATES
    // (0b1|3 bits EXTRA TYPE set here|???|...
    public static enum FLAG_EXTRA_TYPES {
        SCOUT,
        ATTACK,
        VERIFICATION_ENSURANCE,
        DIRECTION_MOVING,
    }
    //NOT SURE interaction between extra stuff (since location data is always location based, but here we have 3-3 bits or 6 bit...
    public static enum FLAG_EXTRA_DATA {

    }

    public static final Direction[] DIRECTIONS = {
            Direction.NORTH,
            Direction.NORTHEAST,
            Direction.EAST,
            Direction.SOUTHEAST,
            Direction.SOUTH,
            Direction.SOUTHWEST,
            Direction.WEST,
            Direction.NORTHWEST,
    };

    public static final Direction[] ordinalDirections = {
            Direction.NORTHEAST,
            Direction.SOUTHEAST,
            Direction.SOUTHWEST,
            Direction.NORTHWEST,
    };

    public static final Direction[] cardinalDirections = {
            Direction.NORTH,
            Direction.EAST,
            Direction.SOUTH,
            Direction.WEST,
    };


    //  SCHEMA 2: 0b | 7 bits schema type (must start with 0) | 3 bits type of bot | 1 bit in danger | 13 bits information

    public static final int MOVEMENT_BOT_SCHEMA_SHIFT = 17;
    public static final int MOVEMENT_BOT_SCHEMA_CODE = 0b0010111;

    public static final int MOVEMENT_BOT_TYPE_SHIFT = 14;
    public static enum MOVEMENT_BOTS_TYPES {
        MUCKRAKER_TYPE,
        POLITICIAN_TYPE,
        SLANDERER_TYPE,
    }

    public static final int MOVEMENT_BOT_DANGER_SHIFT = 13;

    public static final int MOVEMENT_BOTS_DATA_BITMASK = (1 << 13) - 1;
    public static enum MOVEMENT_BOTS_DATA {
        NOT_MOVING,
        MOVING_NORTH,
        MOVING_NORTHEAST,
        MOVING_EAST,
        MOVING_SOUTHEAST,
        MOVING_SOUTH,
        MOVING_SOUTHWEST,
        MOVING_WEST,
        MOVING_NORTHWEST,
        IN_DANGER_MOVE,
    }


    // SCHEMA: 0b011 | 21 bits |


}
