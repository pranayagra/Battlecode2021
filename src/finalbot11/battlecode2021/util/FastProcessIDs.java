package finalbot11.battlecode2021.util;

import battlecode.common.GameActionException;
import battlecode.common.RobotController;

public class FastProcessIDs {

    public static enum TYPE {
        ACTIVE_ATTACKING_POLITICIAN,
        PASSIVE_ATTACKING_POLITICIAN,
        DEFENDING_POLITICIAN,
        GUIDE_SCOUT,
        SCOUT,
    }

    private RobotController controller;

    public int[] robotIDs;
    public TYPE[] typeForRobotID;
    public int[] healthForRobotID;
    public int numRobotsToProcess;

    private static int startIteratorIDX;

    private static int totalPassivePoliticianAttackDamage;


    public FastProcessIDs(int size, RobotController controller) {
        this.controller = controller;
        this.robotIDs = new int[size];
        this.typeForRobotID = new TYPE[size];
        this.healthForRobotID = new int[size];
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

    public void addItem(int robotID, TYPE robotType, int robotHealth) {
        int indexToReplace = numRobotsToProcess;
        if (numRobotsToProcess == robotIDs.length) {
            indexToReplace = (int) (Math.random() * numRobotsToProcess);
            robotIDs[indexToReplace] = robotID;
            typeForRobotID[indexToReplace] = robotType;
            healthForRobotID[indexToReplace] = robotHealth;
        } else {
            robotIDs[indexToReplace] = robotID;
            typeForRobotID[indexToReplace] = robotType;
            healthForRobotID[indexToReplace] = robotHealth;
            numRobotsToProcess++;
        }
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
        typeForRobotID[indexToReplace] = typeForRobotID[indexToKeep];
        healthForRobotID[indexToReplace] = healthForRobotID[indexToKeep];
    }

    public int nextIdxExists() throws GameActionException {
        for (++startIteratorIDX; startIteratorIDX < numRobotsToProcess; ++startIteratorIDX) {
            if (controller.canGetFlag(robotIDs[startIteratorIDX])) {
                // The robot exists, so we can simply return the index
                return controller.getFlag(robotIDs[startIteratorIDX]);
            } else {
                while (--numRobotsToProcess >= startIteratorIDX + 1) {
                    if (controller.canGetFlag(robotIDs[numRobotsToProcess])) {
                        // replace the data in startIteratorIDX by the data in the last index (numRobotsToProcess)
                        replaceIndices(startIteratorIDX, numRobotsToProcess);
                        return controller.getFlag(robotIDs[startIteratorIDX]);
                    }
                }
            }
        }
        return -1;
    }


}