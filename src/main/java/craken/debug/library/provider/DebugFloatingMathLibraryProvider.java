package craken.debug;

import craken.compiler.ir.model.IrType;
import craken.debug.DebugLibraryCallResult.Returned;
import craken.debug.DebugRuntime.Value;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Windows CRT profile 下可由 Java 标量运算稳定解释的 math.h 函数。 */
final class DebugFloatingMathLibraryProvider implements DebugLibraryProvider {
    private final Map<String, DebugLibraryFunction> functions;

    DebugFloatingMathLibraryProvider() {
        LinkedHashMap<String, DebugLibraryFunction> registered = new LinkedHashMap<>();

        registerUnaryFamily(registered, "acos", Math::acos, this::inverseDomain);
        registerUnaryFamily(registered, "asin", Math::asin, this::inverseDomain);
        registerUnaryFamily(registered, "atan", Math::atan, ErrorPolicy.NONE);
        registerBinaryFamily(registered, "atan2", Math::atan2, BinaryErrorPolicy.NONE);
        registerUnaryFamily(registered, "ceil", Math::ceil, ErrorPolicy.NONE);
        registerUnaryFamily(registered, "cos", Math::cos, this::trigonometricError);
        registerUnaryFamily(registered, "cosh", Math::cosh, this::finiteOverflow);
        registerUnaryFamily(registered, "exp", Math::exp, this::exponentialRange);
        registerUnary(registered, "fabs", Precision.DOUBLE, Math::abs, ErrorPolicy.NONE);
        registerUnaryFamily(registered, "floor", Math::floor, ErrorPolicy.NONE);
        registerBinaryFamily(registered, "fmod", (left, right) -> left % right, this::fmodError);
        registered.put("frexp", this::frexp);
        registerUnaryFamily(registered, "log", Math::log, this::logarithmError);
        registerUnaryFamily(registered, "log10", Math::log10, this::logarithmError);
        registered.put("modf", this::modf);
        registered.put("modff", this::modff);
        registerBinaryFamily(registered, "pow", this::cPow, this::powError);
        registerUnaryFamily(registered, "sin", Math::sin, this::trigonometricError);
        registerUnaryFamily(registered, "sinh", Math::sinh, this::finiteOverflow);
        registerUnaryFamily(registered, "sqrt", Math::sqrt, this::squareRootError);
        registerUnaryFamily(registered, "tan", Math::tan, this::trigonometricError);
        registerUnaryFamily(registered, "tanh", Math::tanh, ErrorPolicy.NONE);

        registered.put("ldexp", this::ldexp);
        functions = Map.copyOf(registered);
    }

    @Override
    public String name() {
        return "floating-math";
    }

    @Override
    public Map<String, DebugLibraryFunction> functions() {
        return functions;
    }

    private void registerUnaryFamily(
            Map<String, DebugLibraryFunction> registered,
            String name,
            UnaryOperation operation,
            UnaryErrorPolicy errorPolicy
    ) {
        registerUnary(registered, name, Precision.DOUBLE, operation, errorPolicy);
        registerUnary(registered, name + "f", Precision.FLOAT, operation, errorPolicy);
    }

    private void registerUnary(
            Map<String, DebugLibraryFunction> registered,
            String name,
            Precision precision,
            UnaryOperation operation,
            UnaryErrorPolicy errorPolicy
    ) {
        registered.put(name, (runtime, arguments) -> {
            DebugLibrarySupport.requireCount(name, arguments, 1);
            double input = precision.input(arguments.getFirst());
            double result = precision.result(operation.apply(input));
            setErrno(runtime, errorPolicy.errno(input, result, precision));
            return new Returned(precision.value(result));
        });
    }

    private void registerBinaryFamily(
            Map<String, DebugLibraryFunction> registered,
            String name,
            BinaryOperation operation,
            BinaryErrorPolicy errorPolicy
    ) {
        registerBinary(registered, name, Precision.DOUBLE, operation, errorPolicy);
        registerBinary(registered, name + "f", Precision.FLOAT, operation, errorPolicy);
    }

    private void registerBinary(
            Map<String, DebugLibraryFunction> registered,
            String name,
            Precision precision,
            BinaryOperation operation,
            BinaryErrorPolicy errorPolicy
    ) {
        registered.put(name, (runtime, arguments) -> {
            DebugLibrarySupport.requireCount(name, arguments, 2);
            double left = precision.input(arguments.get(0));
            double right = precision.input(arguments.get(1));
            double result = precision.result(operation.apply(left, right));
            setErrno(runtime, errorPolicy.errno(left, right, result, precision));
            return new Returned(precision.value(result));
        });
    }

    private DebugLibraryCallResult frexp(DebugRuntime runtime, List<Value> arguments) {
        DebugLibrarySupport.requireCount("frexp", arguments, 2);
        double value = arguments.get(0).real();
        long exponentPointer = arguments.get(1).integer();
        int exponent = 0;
        double fraction = value;
        if (Double.isFinite(value) && value != 0.0) {
            int binaryExponent = Math.getExponent(value);
            if (binaryExponent < Double.MIN_EXPONENT) {
                double scaled = Math.scalb(value, 54);
                binaryExponent = Math.getExponent(scaled) - 54;
            }
            exponent = binaryExponent + 1;
            fraction = Math.scalb(value, -exponent);
        }
        runtime.write(exponentPointer, Value.of(IrType.INT, exponent));
        return new Returned(Value.of(IrType.DOUBLE, fraction));
    }

    private DebugLibraryCallResult ldexp(DebugRuntime runtime, List<Value> arguments) {
        DebugLibrarySupport.requireCount("ldexp", arguments, 2);
        double input = arguments.get(0).real();
        int exponent = (int) arguments.get(1).integer();
        double result = Math.scalb(input, exponent);
        setErrno(runtime, scaleRangeError(input, result, Precision.DOUBLE));
        return new Returned(Value.of(IrType.DOUBLE, result));
    }

    private DebugLibraryCallResult modf(DebugRuntime runtime, List<Value> arguments) {
        return splitFraction("modf", runtime, arguments, Precision.DOUBLE);
    }

    private DebugLibraryCallResult modff(DebugRuntime runtime, List<Value> arguments) {
        return splitFraction("modff", runtime, arguments, Precision.FLOAT);
    }

    private DebugLibraryCallResult splitFraction(
            String name,
            DebugRuntime runtime,
            List<Value> arguments,
            Precision precision
    ) {
        DebugLibrarySupport.requireCount(name, arguments, 2);
        double input = precision.input(arguments.get(0));
        double integral;
        double fraction;
        if (Double.isNaN(input)) {
            integral = input;
            fraction = input;
        } else if (Double.isInfinite(input)) {
            integral = input;
            fraction = Math.copySign(0.0, input);
        } else {
            integral = input < 0.0 ? Math.ceil(input) : Math.floor(input);
            fraction = input - integral;
            if (fraction == 0.0) {
                fraction = Math.copySign(0.0, input);
            }
        }
        integral = precision.result(integral);
        fraction = precision.result(fraction);
        runtime.write(arguments.get(1).integer(), precision.value(integral));
        return new Returned(precision.value(fraction));
    }

    private int inverseDomain(double input, double result, Precision precision) {
        return !Double.isNaN(input) && Math.abs(input) > 1.0
                ? DebugLibrarySupport.EDOM
                : 0;
    }

    private int trigonometricError(double input, double result, Precision precision) {
        return Double.isInfinite(input) ? DebugLibrarySupport.EDOM : 0;
    }

    private int logarithmError(double input, double result, Precision precision) {
        if (Double.isNaN(input)) return 0;
        if (input < 0.0) return DebugLibrarySupport.EDOM;
        return input == 0.0 ? DebugLibrarySupport.ERANGE : 0;
    }

    private int squareRootError(double input, double result, Precision precision) {
        return input < 0.0 ? DebugLibrarySupport.EDOM : 0;
    }

    private int finiteOverflow(double input, double result, Precision precision) {
        return Double.isFinite(input) && Double.isInfinite(result)
                ? DebugLibrarySupport.ERANGE
                : 0;
    }

    private int exponentialRange(double input, double result, Precision precision) {
        return scaleRangeError(input, result, precision);
    }

    private int scaleRangeError(double input, double result, Precision precision) {
        return Double.isFinite(input) && Double.isInfinite(result)
                ? DebugLibrarySupport.ERANGE
                : 0;
    }

    private int fmodError(double left, double right, double result, Precision precision) {
        if (Double.isNaN(left) || Double.isNaN(right)) return 0;
        return right == 0.0 || Double.isInfinite(left)
                ? DebugLibrarySupport.EDOM
                : 0;
    }

    private int powError(double left, double right, double result, Precision precision) {
        if (Double.isNaN(left) || Double.isNaN(right)) return 0;
        if (Double.isFinite(left) && left < 0.0
                && Double.isFinite(right) && right != Math.rint(right)) {
            return DebugLibrarySupport.EDOM;
        }
        if (left == 0.0 && right < 0.0) {
            return DebugLibrarySupport.ERANGE;
        }
        if (Double.isFinite(left) && Double.isFinite(right)) {
            if (Double.isInfinite(result)) {
                return DebugLibrarySupport.ERANGE;
            }
        }
        return 0;
    }

    private double cPow(double left, double right) {
        if (right == 0.0 || left == 1.0
                || left == -1.0 && Double.isInfinite(right)) {
            return 1.0;
        }
        return Math.pow(left, right);
    }

    private void setErrno(DebugRuntime runtime, int error) {
        if (error != 0) {
            runtime.setErrno(error);
        }
    }

    private enum Precision {
        DOUBLE(IrType.DOUBLE) {
            @Override double input(Value value) { return value.real(); }
            @Override double result(double value) { return value; }
        },
        FLOAT(IrType.FLOAT) {
            @Override double input(Value value) { return (float) value.real(); }
            @Override double result(double value) { return (float) value; }
        };

        private final IrType type;

        Precision(IrType type) {
            this.type = type;
        }

        abstract double input(Value value);
        abstract double result(double value);

        Value value(double value) {
            return Value.of(type, value);
        }
    }

    @FunctionalInterface
    private interface UnaryOperation {
        double apply(double input);
    }

    @FunctionalInterface
    private interface BinaryOperation {
        double apply(double left, double right);
    }

    @FunctionalInterface
    private interface UnaryErrorPolicy {
        int errno(double input, double result, Precision precision);
    }

    @FunctionalInterface
    private interface BinaryErrorPolicy {
        int errno(double left, double right, double result, Precision precision);

        BinaryErrorPolicy NONE = (left, right, result, precision) -> 0;
    }

    private static final class ErrorPolicy {
        private static final UnaryErrorPolicy NONE = (input, result, precision) -> 0;

        private ErrorPolicy() {
        }
    }
}
