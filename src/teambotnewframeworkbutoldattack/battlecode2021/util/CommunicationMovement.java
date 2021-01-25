package teambotnewframeworkbutoldattack.battlecode2021.util;

public class CommunicationMovement extends Comms {

    /* DONE */

    public static final int FLAG_CODE = 0b011;

    /* 2 bits max */
    public static enum MY_UNIT_TYPE {
        EC,
        MU,
        PO,
        SL,
    }

    // 4 bits max
    public static enum MOVEMENT_BOTS_DATA {
        NOOP,
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

    /* Tells other robots what to do if they read this flag. 3 bits max */
    public static enum COMMUNICATION_TO_OTHER_BOTS {
        NOOP,
        MOVE_TOWARDS_ME,
        MOVE_AWAY_FROM_ME,
        SPOTTED_ENEMY_UNIT,
        SPOTTED_ENEMY_SLANDERER,
        SEND_DEFENDING_POLITICIANS,
    }

    public static int convert_MovementBotData_DirectionInt(MOVEMENT_BOTS_DATA movementBotsData) {
        return (movementBotsData.ordinal() - 2);
    }

    public static MOVEMENT_BOTS_DATA convert_DirectionInt_MovementBotsData(int dir) {
        return MOVEMENT_BOTS_DATA.values()[dir + 2];
    }

    /* SCHEMA: 3 bits code | 1 bit skippedQueue | 1 bit last flag | 2 bits unit type (me) | 4 bits preferred Movement direction | 3 bits other bot actions | 1 bit resetBit | 1 bit inDanger | 8 bits danger direction */
    public static int encodeMovement(
            boolean isUrgent, boolean isLastFlag, MY_UNIT_TYPE myUnitType, MOVEMENT_BOTS_DATA myPreferredMovement, COMMUNICATION_TO_OTHER_BOTS tellOtherBotsAction, boolean resetBit, boolean inDanger, int dangerDirections) {

        return (FLAG_CODE << 21) +
                ((isUrgent ? 1 : 0) << 20) +
                ((isLastFlag ? 1 : 0) << 19) +
                (myUnitType.ordinal() << 17) +
                (myPreferredMovement.ordinal() << 13) +
                (tellOtherBotsAction.ordinal() << 10) +
                ((resetBit ? 1 : 0) << 9) +
                ((inDanger ? 1 : 0) << 8) +
                (dangerDirections & 0xFF);
    }

    public static boolean decodeIsSchemaType(int encoding) {
        return (encoding >> 21) == FLAG_CODE;
    }

    public static MY_UNIT_TYPE decodeMyUnitType(int encoding) {
        return MY_UNIT_TYPE.values()[(encoding >> 17) & 0b11];
    }

    public static MOVEMENT_BOTS_DATA decodeMyPreferredMovement(int encoding) {
        return MOVEMENT_BOTS_DATA.values()[(encoding >> 13) & 0b1111];
    }

    public static COMMUNICATION_TO_OTHER_BOTS decodeCommunicationToOtherBots(int encoding) {
        return COMMUNICATION_TO_OTHER_BOTS.values()[(encoding >> 10) & 0b111];
    }

    public static boolean decodeIsResetBit(int encoding) {
        return ((encoding >> 9) & 1) == 1;
    }

    public static boolean decodeIsDangerBit(int encoding) {
        return ((encoding >> 8) & 1) == 1;
    }

    public static int decodeDangerDirections(int encoding) {
        return (encoding & 0xFF);
    }

}
