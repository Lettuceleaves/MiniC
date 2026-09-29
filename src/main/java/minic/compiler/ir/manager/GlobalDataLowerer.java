package minic.compiler.ir.manager;

import minic.compiler.ir.model.IrGlobalData;
import minic.compiler.parser.node.Declaration.GlobalVarDecl;
import minic.compiler.parser.node.Expression;
import minic.compiler.parser.node.Expression.*;
import minic.compiler.semantic.model.StructLayout;
import minic.compiler.type.MiniType;
import minic.compiler.type.TypeLayout;
import minic.compiler.lexer.token.TokenType;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Converts C constant initializers into the target's little-endian writable data image. */
public final class GlobalDataLowerer {
    private final Map<String, StructLayout> layouts;

    public GlobalDataLowerer(Map<String, StructLayout> layouts) { this.layouts = Map.copyOf(layouts); }

    public List<IrGlobalData> lower(List<GlobalVarDecl> declarations) {
        ArrayList<IrGlobalData> result = new ArrayList<>();
        java.util.LinkedHashMap<String, List<GlobalVarDecl>> groups = new java.util.LinkedHashMap<>();
        declarations.forEach(declaration -> groups.computeIfAbsent(declaration.name(), ignored -> new ArrayList<>())
                .add(declaration));
        for (List<GlobalVarDecl> group : groups.values()) {
            GlobalVarDecl declaration = group.stream().filter(item -> item.initializerOptional().isPresent())
                    .findFirst().orElseGet(() -> group.stream().filter(item -> !item.external()).findFirst().orElse(null));
            if (declaration == null) continue;
            byte[] bytes = new byte[sizeOf(declaration.type())];
            declaration.initializerOptional().ifPresent(value -> write(bytes, 0, declaration.type(), value));
            int alignment = alignmentOf(declaration.type());
            for (var spec : declaration.alignmentSpecs()) {
                alignment = Math.max(alignment, spec.constant() != null
                        ? spec.constant() : alignmentOf(spec.type()));
            }
            result.add(new IrGlobalData(declaration.name(), declaration.type(), bytes, alignment));
        }
        return List.copyOf(result);
    }

    private void write(byte[] output, int offset, MiniType type, Expression initializer) {
        type = type.unqualified();
        if (initializer instanceof DesignatedInitExpr designated) initializer = designated.value();
        if (initializer instanceof AggregateInitExpr aggregate) {
            writeAggregate(output, offset, type, aggregate);
            return;
        }
        Number value = constant(initializer);
        ByteBuffer buffer = ByteBuffer.wrap(output).order(ByteOrder.LITTLE_ENDIAN);
        if (type.isPointer() || type.isVaList()) buffer.putLong(offset, value.longValue());
        else if (type == MiniType.FLOAT) buffer.putFloat(offset, value.floatValue());
        else if (type == MiniType.DOUBLE) buffer.putDouble(offset, value.doubleValue());
        else switch (sizeOf(type)) {
            case 1 -> buffer.put(offset, value.byteValue());
            case 2 -> buffer.putShort(offset, value.shortValue());
            case 4 -> buffer.putInt(offset, value.intValue());
            case 8 -> buffer.putLong(offset, value.longValue());
            default -> throw new IllegalArgumentException("initializer is not scalar: " + type);
        }
    }

    private void writeAggregate(byte[] output, int offset, MiniType type, AggregateInitExpr aggregate) {
        if (type instanceof MiniType.ArrayType array) {
            int current = 0;
            int stride = sizeOf(array.elementType());
            for (Expression item : aggregate.values()) {
                if (item instanceof DesignatedInitExpr designated
                        && designated.designators().getFirst() instanceof Designator.Index index) {
                    current = index.index();
                    writeDesignated(output, offset + current * stride, array.elementType(), designated, 1);
                } else {
                    if (current >= array.length()) break;
                    write(output, offset + current * stride, array.elementType(), item);
                }
                current++;
            }
            return;
        }
        if (type instanceof MiniType.StructType struct) {
            StructLayout layout = layout(struct.name());
            int current = 0;
            for (Expression item : aggregate.values()) {
                if (item instanceof DesignatedInitExpr designated
                        && designated.designators().getFirst() instanceof Designator.Field field) {
                    var target = layout.field(field.name()).orElseThrow();
                    current = directFieldIndex(layout, field.name());
                    writeDesignated(output, offset + target.offset(), target.type(), designated, 1);
                } else {
                    if (current >= layout.fields().size()) break;
                    var target = layout.fields().get(current);
                    write(output, offset + target.offset(), target.type(), item);
                }
                current = current < 0 ? layout.fields().size() : current + 1;
                if (struct.name().startsWith("$union$")) break;
            }
            return;
        }
        if (!aggregate.values().isEmpty()) write(output, offset, type, aggregate.values().getFirst());
    }

    private void writeDesignated(byte[] output, int offset, MiniType type,
                                 DesignatedInitExpr designated, int pathIndex) {
        for (int i = pathIndex; i < designated.designators().size(); i++) {
            Designator part = designated.designators().get(i);
            if (part instanceof Designator.Index index && type.unqualified() instanceof MiniType.ArrayType array) {
                offset += index.index() * sizeOf(array.elementType());
                type = array.elementType();
            } else if (part instanceof Designator.Field field
                    && type.unqualified() instanceof MiniType.StructType struct) {
                var target = layout(struct.name()).field(field.name()).orElseThrow();
                offset += target.offset();
                type = target.type();
            } else throw new IllegalArgumentException("initializer designator does not match type");
        }
        write(output, offset, type, designated.value());
    }

    private Number constant(Expression expression) {
        return switch (expression) {
            case BoolLiteralExpr value -> value.value() ? 1L : 0L;
            case CharLiteralExpr value -> (long) value.value();
            case IntegerLiteralExpr value -> (long) value.value();
            case IntegerConstantExpr value -> value.value();
            case LongLiteralExpr value -> value.value();
            case FloatLiteralExpr value -> value.value();
            case DoubleLiteralExpr value -> value.value();
            case NullLiteralExpr ignored -> 0L;
            case GroupingExpr group -> constant(group.expression());
            case CastExpr cast -> constant(cast.operand());
            case CommaExpr comma -> constant(comma.expressions().getLast());
            case UnaryExpr unary -> unary(unary.operator(), constant(unary.operand()));
            case BinaryExpr binary -> binary(binary.operator(), constant(binary.left()), constant(binary.right()));
            default -> throw new IllegalArgumentException("global initializer is not a constant expression: "
                    + expression.getClass().getSimpleName());
        };
    }

    private Number unary(TokenType operator, Number value) {
        return switch (operator) {
            case PLUS -> value;
            case MINUS -> value instanceof Float || value instanceof Double ? -value.doubleValue() : -value.longValue();
            case TILDE -> ~value.longValue();
            case BANG -> value.doubleValue() == 0 ? 1L : 0L;
            default -> throw new IllegalArgumentException("unsupported constant unary operator: " + operator);
        };
    }

    private Number binary(TokenType op, Number left, Number right) {
        boolean real = left instanceof Float || left instanceof Double || right instanceof Float || right instanceof Double;
        double a = left.doubleValue(), b = right.doubleValue();
        long x = left.longValue(), y = right.longValue();
        return switch (op) {
            case PLUS -> real ? a + b : x + y;
            case MINUS -> real ? a - b : x - y;
            case STAR -> real ? a * b : x * y;
            case SLASH -> real ? a / b : x / y;
            case PERCENT -> x % y;
            case AMPERSAND -> x & y;
            case PIPE -> x | y;
            case CARET -> x ^ y;
            case LESS_LESS -> x << y;
            case GREATER_GREATER -> x >> y;
            case EQUAL_EQUAL -> a == b ? 1L : 0L;
            case BANG_EQUAL -> a != b ? 1L : 0L;
            case LESS -> a < b ? 1L : 0L;
            case LESS_EQUAL -> a <= b ? 1L : 0L;
            case GREATER -> a > b ? 1L : 0L;
            case GREATER_EQUAL -> a >= b ? 1L : 0L;
            case AMPERSAND_AMPERSAND -> a != 0 && b != 0 ? 1L : 0L;
            case PIPE_PIPE -> a != 0 || b != 0 ? 1L : 0L;
            default -> throw new IllegalArgumentException("unsupported constant binary operator: " + op);
        };
    }

    private int sizeOf(MiniType type) {
        type = type.unqualified();
        if (type instanceof MiniType.ArrayType array) return Math.multiplyExact(sizeOf(array.elementType()), array.length());
        if (type instanceof MiniType.StructType struct) return layout(struct.name()).size();
        return TypeLayout.sizeOf(type);
    }
    private int alignmentOf(MiniType type) {
        type = type.unqualified();
        if (type instanceof MiniType.ArrayType array) return alignmentOf(array.elementType());
        if (type instanceof MiniType.StructType struct) return layout(struct.name()).alignment();
        return TypeLayout.alignmentOf(type);
    }
    private StructLayout layout(String name) {
        StructLayout layout = layouts.get(name);
        if (layout == null) throw new IllegalArgumentException("missing struct layout: " + name);
        return layout;
    }
    private int directFieldIndex(StructLayout layout, String name) {
        for (int i = 0; i < layout.fields().size(); i++) if (layout.fields().get(i).name().equals(name)) return i;
        return -1;
    }
}
