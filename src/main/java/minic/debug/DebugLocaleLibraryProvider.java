package minic.debug;

import minic.compiler.ir.model.IrType;
import minic.debug.DebugLibraryCallResult.Returned;
import minic.debug.DebugRuntime.Value;

import java.util.List;
import java.util.Map;

/** Windows C locale 中可由 Debug runtime 确定解释的函数。 */
final class DebugLocaleLibraryProvider implements DebugLibraryProvider {
    private final Map<String, DebugLibraryFunction> functions = Map.of(
            "setlocale", this::setLocale,
            "localeconv", this::localeConvention
    );

    @Override
    public String name() {
        return "locale";
    }

    @Override
    public Map<String, DebugLibraryFunction> functions() {
        return functions;
    }

    private DebugLibraryCallResult setLocale(DebugRuntime runtime, List<Value> arguments) {
        DebugLibrarySupport.requireCount("setlocale", arguments, 2);
        int category = (int) arguments.get(0).integer();
        long locale = arguments.get(1).integer();
        return new Returned(Value.of(IrType.POINTER, runtime.setLocale(category, locale)));
    }

    private DebugLibraryCallResult localeConvention(DebugRuntime runtime, List<Value> arguments) {
        DebugLibrarySupport.requireCount("localeconv", arguments, 0);
        return new Returned(Value.of(IrType.POINTER, runtime.localeConventionAddress()));
    }
}
