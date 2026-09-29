package minic.compiler.asm;

import java.util.List;

final class CallingConvention {
    static final String ENTRY_SYMBOL = "minic$entry";
    static final String USER_MAIN_SYMBOL = "main";
    static final List<String> INTEGER_ARGUMENT_REGISTERS = List.of("ecx", "edx", "r8d", "r9d");
    static final List<String> POINTER_ARGUMENT_REGISTERS = List.of("rcx", "rdx", "r8", "r9");
    static final List<String> FLOAT_ARGUMENT_REGISTERS = List.of("xmm0", "xmm1", "xmm2", "xmm3");

    private CallingConvention() {
    }

    static String symbolName(String functionName) {
        return "minic$" + functionName;
    }

    static String functionDefinitionSymbol(String functionName) {
        if (USER_MAIN_SYMBOL.equals(functionName)) {
            return USER_MAIN_SYMBOL;
        }
        return symbolName(functionName);
    }

    static String callSymbol(String functionName, boolean external) {
        if (external) {
            return functionName;
        }
        if (USER_MAIN_SYMBOL.equals(functionName)) {
            return USER_MAIN_SYMBOL;
        }
        return symbolName(functionName);
    }

    static boolean isRegisterArgument(int argumentIndex) {
        return argumentIndex < INTEGER_ARGUMENT_REGISTERS.size();
    }

    static String integerArgumentRegister(int argumentIndex) {
        return INTEGER_ARGUMENT_REGISTERS.get(argumentIndex);
    }

    static String pointerArgumentRegister(int argumentIndex) {
        return POINTER_ARGUMENT_REGISTERS.get(argumentIndex);
    }

    static String floatArgumentRegister(int argumentIndex) {
        return FLOAT_ARGUMENT_REGISTERS.get(argumentIndex);
    }

    static int incomingStackArgumentOffset(int argumentIndex) {
        if (isRegisterArgument(argumentIndex)) {
            throw new IllegalArgumentException("register argument is in the incoming home area, not the stack tail");
        }
        return incomingArgumentSlotOffset(argumentIndex);
    }

    /**
     * Canonical address of an argument's eight-byte Windows x64 slot relative
     * to a frame pointer established after {@code push rbp}.  Slots 0..3 are
     * the caller-provided home area; slot 4 and later are stack arguments.
     */
    static int incomingArgumentSlotOffset(int argumentIndex) {
        if (argumentIndex < 0) {
            throw new IllegalArgumentException("argument index must be non-negative");
        }
        return 16 + argumentIndex * 8;
    }

    static int outgoingStackArgumentOffset(int argumentIndex) {
        return 32 + (argumentIndex - INTEGER_ARGUMENT_REGISTERS.size()) * 8;
    }

    static int outgoingArgumentAreaSize(int maxArgumentCount) {
        int stackArgumentCount = Math.max(0, maxArgumentCount - INTEGER_ARGUMENT_REGISTERS.size());
        return alignTo16(32 + stackArgumentCount * 8);
    }

    static int alignTo16(int value) {
        return ((value + 15) / 16) * 16;
    }
}
