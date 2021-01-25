package teambotnewestcodebutoldattack.battlecode2021.util;

import battlecode.common.GameActionException;
import battlecode.common.RobotController;

public class FastProcessIDs {

    public static enum TYPE {
        ACTIVE_ATTACKING_POLITICIAN,
        PASSIVE_ATTACKING_POLITICIAN,
        GUIDE_SCOUT,
        SCOUT,
    }

    private RobotController controller;

    public int[] robotIDs; // [12912, 14911, 12913, 13991, 19391]
    public int[] numFlagsForRobotID;
    public TYPE[] typeForRobotID;
    public int[] healthForRobotID;
    public int[][] flagsForRobotID; // [robotID IDX][#flags]
    public int numRobotsToProcess;

    private static int startIteratorIDX;

    private static int totalPassivePoliticianAttackDamage;


    public FastProcessIDs(int size, int maxFlags, RobotController controller) {
        this.controller = controller;
        this.robotIDs = new int[size];
        this.numFlagsForRobotID = new int[size];
        this.typeForRobotID = new TYPE[size];
        this.healthForRobotID = new int[size];
        this.flagsForRobotID = new int[size][maxFlags];
        this.numRobotsToProcess = 0;
        this.startIteratorIDX = -1;
        totalPassivePoliticianAttackDamage = 0;
    }

    public void resetSize() {
        numRobotsToProcess = 0;
    }

    public void resetIterator() {
        startIteratorIDX = -1;
        totalPassivePoliticianAttackDamage = 0;
    }

    public int currentSize() {
        return numRobotsToProcess;
    }

    public boolean addItem(int robotID, TYPE robotType, int robotHealth) {
        //TODO: deal with overflow
        if (numRobotsToProcess == robotIDs.length) {
            Debug.printInformation("OVERFLOWED WITH NUMBER OF ROBOTS TO PROCESS ", " ERROR ");
            return false;
        }
        robotIDs[numRobotsToProcess] = robotID;
        numFlagsForRobotID[numRobotsToProcess] = 0;
        typeForRobotID[numRobotsToProcess] = robotType;
        healthForRobotID[numRobotsToProcess] = robotHealth;
        numRobotsToProcess++;
        return true;
    }

    public void updatePassivePoliticianAttackDamage(int robotIDX) {
        if (typeForRobotID[robotIDX] == TYPE.PASSIVE_ATTACKING_POLITICIAN) {
            totalPassivePoliticianAttackDamage += Math.max(0, healthForRobotID[robotIDX] - 10);
        }
    }

    public int getPassivePoliticianAttackDamage() {
        return totalPassivePoliticianAttackDamage;
    }

    public void replaceIndices(int indexToReplace, int indexToKeep) {
        robotIDs[indexToReplace] = robotIDs[indexToKeep];
        numFlagsForRobotID[indexToReplace] = numFlagsForRobotID[indexToKeep];
        typeForRobotID[indexToReplace] = typeForRobotID[indexToKeep];
        for (int i = 0; i < numFlagsForRobotID[indexToKeep]; ++i) {
            flagsForRobotID[indexToReplace][i] = flagsForRobotID[indexToKeep][i];
        }
    }

    public boolean nextIdxExists() throws GameActionException {
        for (++startIteratorIDX; startIteratorIDX < numRobotsToProcess; ++startIteratorIDX) {
            if (controller.canGetFlag(robotIDs[startIteratorIDX])) {
                // The robot exists, so we can simply return the index
                return true;
            } else {
                while (--numRobotsToProcess >= startIteratorIDX + 1) {
                    if (controller.canGetFlag(robotIDs[numRobotsToProcess])) {
                        // replace the data in startIteratorIDX by the data in the last index (numRobotsToProcess)
                        replaceIndices(startIteratorIDX, numRobotsToProcess);
                        return true;
                    }
                }

            }
        }
        return false;
    }


}
