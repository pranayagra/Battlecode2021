enum Instruction
{
    RUSH (0b1111),
    EXPLORE (0b0111),
    DEFEND (0b0011),
    SIGNAL (0b0001),
    NOSIG (0b0000);

    private final code;

    Instruction(byte code) {
        this.code = code;
    }

    byte getCode() {
        return code;
    }

}

