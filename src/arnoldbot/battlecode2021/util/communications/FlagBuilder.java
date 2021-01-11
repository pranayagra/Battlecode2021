package teambot.battlecode2021.util;

import java.util.ArrayList;

import battlecode.common.RobotController;
import battlecode.common.Team;


// EC Channels: | Unit Flags:  8 | Danger: 1 | State: 2 | State Strategy: 3 | Instruction: 4 | Param 1: 3 | Param 2: 3 |
// EC Channels: | 6 ID | 6 X | 6 Y | 4 Instruction | 1 Parity | 1 Extra |
// Unit Channels: | 6 ID ? | 6 x | 6 y | 


public class FlagBuilder {

    public static final IDENTIFIER = 24 - 6;
    public static final LOCATION = IDENTIFIER -  12;
    public static final INSTRUCTION = LOCATION - 4;
    public static final PARITY = INSTRUCTION - 1;
    public static final EXTRA = PARITY - 1;

    private int flag;

    public static int getInstruction(int flag) {
        return (this.flag >>> INSTRUCTION) & ((byte) 0b1111);
    }

    public static int getID(int flag) {
        return (this.flag >> LOCATION) & ((byte) 0xFFFF);
    }

    public static MapLocation getLocation(int flag) {
        return MapLocation((int) (this.flag >>> (LOCATION + 6)) & ((byte) 0b111111), (int) ((this.flag >>> (LOCATION) & ((byte) 0b111111)));
    }

    public Communication(int seed) {
        this.flag = seed;
    }

    public Communication (int flag, int seed) {
        this.flag = flag;
    }

    public void setID_most(int id) {
        this.flag &= ((byte) 0x0000FFFF >> 2);
        this.flag |=  ((((byte) id >> 16) & 0xFFFF) <<< LOCATION);
    }

    public void setID_least(int id) {
        this.flag &= ((byte) 0x0000FFFF >> 2);
        this.flag |= ((((byte) id) & 0xFFFF) <<< LOCATION);
    }


    public void setParity(boolean val) {
        this.flag |= ((val ? 1 : 0) << PARITY);
    }

    public void setInstruction(Instruction inst) {
        this.flag &= (byte) 0x00F >> 2; 
        this.flag |= (inst.getBinary() << INSTRUCTION);
    }

    public void setLocation(MapLocation loc) {
        this.flag &= (byte) 0x000FF >> 2;
        this.flag |= (((byte) loc.X << 6 | (byte) loc.Y) << LOCATION);
    }

    public int getFlag() {
        return this.flag;
    }

}
