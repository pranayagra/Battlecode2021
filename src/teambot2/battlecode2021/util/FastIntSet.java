package teambot2.battlecode2021.util;

public class FastIntSet {
    private int[] array;
    private int logicalSize;

    public FastIntSet(int size) {
        array = new int[size];
        logicalSize = 0;
    }

    public void add(int item) {
        array[logicalSize++] = item;
    }



}
