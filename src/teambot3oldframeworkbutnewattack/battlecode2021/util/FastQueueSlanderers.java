package teambot3oldframeworkbutnewattack.battlecode2021.util;

public class FastQueueSlanderers {
    private int[][] array; //array[i][0,1], where 0=ID, 1=creation time
    private int front;
    private int size;

    public FastQueueSlanderers(int arraySize) {
        array = new int[arraySize][2];
    }

    public void push(int ID, int creationRound) {
        array[(front + (size++)) % array.length][0] = ID;
        array[(front + (size++)) % array.length][1] = creationRound;
    }

    public void removeFront() {
        size--; front++;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public int getSize() {
        return size;
    }

    public void reset() {
        size = 0;
    }

    public int getFrontID() {
        return array[front % array.length][0];
    }

    public int getFrontCreationTime() {
        return array[front % array.length][1];
    }

}
