package teambotnew2codebutoldattack.battlecode2021.util;

public class CommunicationRobotID extends Comms {

    /* DONE */

    public static final int FLAG_CODE = 0b010;

    /* 2 bits max */
    public enum COMMUNICATION_UNIT_TYPE {
        EC,
        MU,
        PO,
        SL,
    }

    /* 2 bits max */
    public enum COMMUNICATION_UNIT_TEAM {
        NOOP,
        MY,
        ENEMY,
        NEUTRAL
    }

    /* SCHEMA: 3 bits code | 1 bit skippedQueue | 1 bit last flag | 2 bits unit type (thing I am trying to communicate) | 2 bits unit team | 15 bits robotID */
    /* MAX robot ID is 42767 */
    public static int encodeRobotID(
            boolean isUrgent, boolean lastFlag, COMMUNICATION_UNIT_TYPE communicatedUnitType, COMMUNICATION_UNIT_TEAM communicatedUnitTeam, int robotID) {
        robotID = Math.min(42767, robotID);
        return (FLAG_CODE << 21) +
                ((isUrgent ? 1 : 0) << 20) +
                ((lastFlag ? 1 : 0) << 19) +
                (communicatedUnitType.ordinal() << 17) +
                (communicatedUnitTeam.ordinal() << 15) +
                ((robotID - 10000) & 0x7FFF);
    }

    public static boolean decodeIsSchemaType(int encoding) {
        return (encoding >> 21) == FLAG_CODE;
    }

    public static COMMUNICATION_UNIT_TYPE decodeCommunicatedUnitType(int encoding) {
        return COMMUNICATION_UNIT_TYPE.values()[(encoding >> 17) & 0b11];
    }

    public static COMMUNICATION_UNIT_TEAM decodeCommunicatedUnitTeam(int encoding) {
        return COMMUNICATION_UNIT_TEAM.values()[(encoding >> 15) & 0b11];
    }

    public static int decodeRobotID(int encoding) {
        return (encoding & 0x7FFF) + 10000;
    }

}
