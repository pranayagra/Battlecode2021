package teambot2.battlecode2021.util;

public class CommunicationHealth extends Comms {

    /* DONE */

    public static final int FLAG_CODE = 0b100;

    /* 2 bits max */
    public static enum COMMUNICATION_UNIT_TEAM {
        ENEMY_BUTNOTEC, //useful for sending how strong the opponents units are to guide our spawn (BE CAREFUL USING/CHECKING THIS ELSEWHERE)
        CONVERTING_TO_PASSIVE_POLITICIAN,
        ENEMY,
        NEUTRAL
    }

    /* SCHEMA: 3 bits code | 1 bit skippedQueue | 1 bit last flag | 2 bits unit team | 17 bits health */
    // MAX robotHealth is 131071
    public static int encodeECInfo(
            boolean isUrgent, boolean isLastFlag, COMMUNICATION_UNIT_TEAM communicatedUnitTeam, int robotHealth) {
        robotHealth = Math.min(131071, robotHealth);
        return (FLAG_CODE << 21) +
                ((isUrgent ? 1 : 0) << 20) +
                ((isLastFlag ? 1 : 0) << 19) +
                (communicatedUnitTeam.ordinal() << 17) +
                (robotHealth & 0x1FFFF);
    }

    public static boolean decodeIsSchemaType(int encoding) {
        return (encoding >> 21) == FLAG_CODE;
    }

    public static COMMUNICATION_UNIT_TEAM decodeCommunicatedUnitTeam(int encoding) {
        return COMMUNICATION_UNIT_TEAM.values()[(encoding >> 17) & 0b11];
    }

    public static int decodeRobotHealth(int encoding) {
        return (encoding & 0x1FFFF);
    }

}
