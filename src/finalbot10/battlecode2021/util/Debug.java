package finalbot10.battlecode2021.util;

import battlecode.common.*;

import java.util.Arrays;

public class Debug {
    public static boolean debug = true;
    public static boolean debug2 = false;

    public static boolean debugByteCode = true;

    public static RobotController controller;

    public static void init(RobotController controller) {
        Debug.controller = controller;
    }

    private static RobotType robotType() {
        return Cache.ROBOT_TYPE;
    }

    private static MapLocation robotLocation() {
        return Cache.CURRENT_LOCATION;
    }

    private static int robotInfluence() {
        return Cache.INFLUENCE;
    }

    private static int robotConviction() {
        return Cache.CONVICTION;
    }

    private static int mapWidth() {
        return Cache.MAP_WIDTH;
    }

    private static int mapHeight() {
        return Cache.MAP_HEIGHT;
    }

    private static int mapLeftEdge() {
        return Cache.MAP_LEFT;
    }

    private static int mapRightEdge() {
        return Cache.MAP_RIGHT;
    }

    private static int mapTopEdge() {
        return Cache.MAP_TOP;
    }

    private static int mapBotEdge() {
        return Cache.MAP_BOTTOM;
    }

    private static void assertValues() {
        assert robotType() == controller.getType();
        assert robotLocation() == controller.getLocation();
        assert robotInfluence() == controller.getInfluence();
        assert robotConviction() == controller.getConviction();
    }

    public static void printByteCode(String msg) {
        if (debugByteCode) {
           System.out.println(msg + ": " + robotType() + " at " + robotLocation() + " has " + Clock.getBytecodesLeft() + " ByteCode Left");
        }
    }

    public static void printInformation(String uniqueMSG, Object data) {
        if (debug) {
            String objectPrint = "NULL object";
            if (data != null) objectPrint = data.toString();
           System.out.println(uniqueMSG + " => " + robotType() + " at " + robotLocation() + ": " + objectPrint);
        }
    }

    public static void printInformationArray(String uniqueMSG, Object[] data) {
        if (debug) {
           System.out.println(uniqueMSG + " => " + robotType() + " at " + robotLocation() + ": " + Arrays.toString(data));
        }
    }

    public static void printLocation(MapLocation location) {
        if (debug) {
          // System.out.println(robotType() + " at " + robotLocation() + " has location " + location);
        }
    }

    public static void printFlag(int flag) {
        if (debug) {
           System.out.println(robotType() + " at " + robotLocation() + " has flag " + flag);
        }
    }

    public static void printRobotInformation() {
//        assertValues();
        if (debug) {
           System.out.println(robotType() + " at " + robotLocation() + " has: " + robotInformationOutput());
        }
    }

    public static void printMapInformation() {
//        assertValues();
        if (debug) {
           System.out.println(robotType() + " at " + robotLocation() + " knows: " + mapInformationOutput());
        }
    }

    private static String mapInformationOutput() {
        return "[Map Width: " + mapWidth() + ", Map Height: " + mapHeight() + ", Map Left Edge: " +
                mapLeftEdge() + ", Map Right Edge: " + mapRightEdge() + ", Map Top Edge: " + mapTopEdge() + ", Map Bot Edge: " + mapBotEdge() + "]";
    }

    private static String robotInformationOutput() {
        return "[ByteCode Left: " + Clock.getBytecodesLeft() + ", Influence: " + robotInfluence() + ", Conviction: " + robotConviction() + "]";
    }

    public static void resignGame(int round) {
        if (debug) {
            if (controller.getRoundNum() >= round) controller.resign();
        }
    }

}
