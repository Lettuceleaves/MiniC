package minic.compiler.codegen.windows;

import java.util.List;

final class WindowsX64CallingConvention {
    static final String ENTRY_SYMBOL = "main";
    static final List<String> INTEGER_ARGUMENT_REGISTERS = List.of("ecx", "edx", "r8d", "r9d");

    private WindowsX64CallingConvention() {
    }

    static String symbolName(String functionName) {
        if (ENTRY_SYMBOL.equals(functionName)) {
            return ENTRY_SYMBOL;
        }
        return "minic$" + functionName;
    }

    static boolean isRegisterArgument(int argumentIndex) {
        return argumentIndex < INTEGER_ARGUMENT_REGISTERS.size();
    }

    static String integerArgumentRegister(int argumentIndex) {
        return INTEGER_ARGUMENT_REGISTERS.get(argumentIndex);
    }

    static int incomingStackArgumentOffset(int argumentIndex) {
        return 48 + (argumentIndex - INTEGER_ARGUMENT_REGISTERS.size()) * 8;
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
