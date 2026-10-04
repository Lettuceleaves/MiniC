package craken.compiler.ir.manager;

import craken.compiler.ir.model.IrGlobalData;
import craken.compiler.parser.node.Declaration.GlobalVarDecl;
import craken.compiler.parser.node.Expression;
import craken.compiler.parser.node.Expression.*;
import craken.compiler.semantic.model.StructLayout;
import craken.compiler.type.CrakenType;
import craken.compiler.type.TypeLayout;
import craken.compiler.type.TemplateArgument;
import craken.compiler.type.TemplateValues;
import craken.compiler.lexer.token.TokenType;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Converts C constant initializers into the target's little-endian writable data image. */
public final class GlobalDataLowerer {
    private static final craken.SourceRange CONSTANT_RANGE = new craken.SourceRange(1, 0, 1, 0);
    private final Map<String, StructLayout> layouts;
    private final Map<Expression, CrakenType> expressionTypes;
    private final Map<String,CrakenType> symbols;
    private final StringLiteralRegistry strings;
    private List<IrGlobalData.Address> addresses;
    private record AddressValue(String symbol,long addend,IrGlobalData.AddressKind kind) {
        AddressValue plus(long amount) {return new AddressValue(symbol,Math.addExact(addend,amount),kind);}
    }

    public GlobalDataLowerer(Map<String, StructLayout> layouts) { this(layouts, Map.of()); }

    public GlobalDataLowerer(Map<String, StructLayout> layouts, Map<Expression, CrakenType> expressionTypes) {
        this(layouts,expressionTypes,new StringLiteralRegistry(),Map.of());
    }

    public GlobalDataLowerer(Map<String,StructLayout> layouts,Map<Expression,CrakenType> expressionTypes,
                             StringLiteralRegistry strings,Map<String,CrakenType> symbols) {
        this.strings=java.util.Objects.requireNonNull(strings);
        this.symbols=new java.util.LinkedHashMap<>(symbols);
        this.layouts = Map.copyOf(layouts);
        this.expressionTypes = java.util.Collections.unmodifiableMap(new java.util.IdentityHashMap<>(expressionTypes));
    }

    public List<IrGlobalData> lower(List<GlobalVarDecl> declarations) {
        ArrayList<IrGlobalData> result = new ArrayList<>();
        declarations.forEach(declaration->symbols.put(declaration.name(),declaration.type()));
        java.util.LinkedHashMap<String, List<GlobalVarDecl>> groups = new java.util.LinkedHashMap<>();
        declarations.forEach(declaration -> groups.computeIfAbsent(declaration.name(), ignored -> new ArrayList<>())
                .add(declaration));
        for (List<GlobalVarDecl> group : groups.values()) {
            GlobalVarDecl declaration = group.stream().filter(item -> item.initializerOptional().isPresent())
                    .findFirst().orElseGet(() -> group.stream().filter(item -> !item.external()).findFirst().orElse(null));
            if (declaration == null) continue;
            byte[] bytes = new byte[sizeOf(declaration.type())];
            addresses=new ArrayList<>();
            declaration.initializerOptional().ifPresent(value -> write(bytes, 0, declaration.type(), value));
            int alignment = alignmentOf(declaration.type());
            for (var spec : declaration.alignmentSpecs()) {
                alignment = Math.max(alignment, spec.constant() != null
                        ? spec.constant() : alignmentOf(spec.type()));
            }
            result.add(new IrGlobalData(declaration.name(), declaration.type(), bytes, alignment,
                    addresses, declaration.range()));
        }
        return List.copyOf(result);
    }

    private void write(byte[] output, int offset, CrakenType type, Expression initializer) {
        type = type.unqualified();
        // A later C designator replaces the entire selected subobject, including its relocations.
        int limit = Math.addExact(offset, sizeOf(type));
        addresses.removeIf(address -> address.offset() < limit && address.offset() + Long.BYTES > offset);
        java.util.Arrays.fill(output, offset, limit, (byte) 0);
        if (initializer instanceof DesignatedInitExpr designated) initializer = designated.value();
        if (initializer instanceof AggregateInitExpr aggregate) {
            writeAggregate(output, offset, type, aggregate);
            return;
        }
        if(type.isArray()&&initializer instanceof StringLiteralExpr literal) {
            byte[] bytes=StringLiteralRegistry.encode(literal.value(),literal.encoding());
            if(bytes.length>sizeOf(type)+sizeOf(type.elementType()))throw new IllegalArgumentException("String does not fit static array");
            System.arraycopy(bytes,0,output,offset,Math.min(bytes.length,sizeOf(type)));return;
        }
        if(type.isPointer()) {
            AddressValue address=addressValue(initializer);
            if(address!=null){addresses.add(new IrGlobalData.Address(offset,address.symbol,address.addend,address.kind));return;}
        }
        Number value = convert(constantValue(initializer), type).value();
        ByteBuffer buffer = ByteBuffer.wrap(output).order(ByteOrder.LITTLE_ENDIAN);
        if (type.isPointer() || type.isVaList()) buffer.putLong(offset, value.longValue());
        else if (type == CrakenType.FLOAT) buffer.putFloat(offset, value.floatValue());
        else if (type.isFloatingScalar()) buffer.putDouble(offset, value.doubleValue());
        else switch (sizeOf(type)) {
            case 1 -> buffer.put(offset, value.byteValue());
            case 2 -> buffer.putShort(offset, value.shortValue());
            case 4 -> buffer.putInt(offset, value.intValue());
            case 8 -> buffer.putLong(offset, value.longValue());
            default -> throw new IllegalArgumentException("initializer is not scalar: " + type);
        }
    }

    private void writeAggregate(byte[] output, int offset, CrakenType type, AggregateInitExpr aggregate) {
        if (type instanceof CrakenType.ArrayType array) {
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
        if (type instanceof CrakenType.StructType struct) {
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

    private void writeDesignated(byte[] output, int offset, CrakenType type,
                                 DesignatedInitExpr designated, int pathIndex) {
        for (int i = pathIndex; i < designated.designators().size(); i++) {
            Designator part = designated.designators().get(i);
            if (part instanceof Designator.Index index && type.unqualified() instanceof CrakenType.ArrayType array) {
                offset += index.index() * sizeOf(array.elementType());
                type = array.elementType();
            } else if (part instanceof Designator.Field field
                    && type.unqualified() instanceof CrakenType.StructType struct) {
                var target = layout(struct.name()).field(field.name()).orElseThrow();
                offset += target.offset();
                type = target.type();
            } else throw new IllegalArgumentException("initializer designator does not match type");
        }
        write(output, offset, type, designated.value());
    }

    private AddressValue addressValue(Expression expression) {
        if(expression instanceof GroupingExpr group)return addressValue(group.expression());
        if(expression instanceof CastExpr cast) {
            // Only pointer-preserving casts keep a symbolic relocation. An intermediate
            // integer cast may truncate an address and cannot be discarded.
            return cast.targetType().isPointer() ? addressValue(cast.operand()) : null;
        }
        if(expression instanceof StringLiteralExpr literal)return new AddressValue(strings.define(literal.value(),literal.encoding()).label(),0,IrGlobalData.AddressKind.STRING);
        if(expression instanceof NameExpr name) {
            CrakenType type=declaredType(name);
            if(type!=null&&type.isFunction())return new AddressValue(name.name(),0,IrGlobalData.AddressKind.FUNCTION);
            if(type!=null&&type.isArray())return new AddressValue(name.name(),0,IrGlobalData.AddressKind.OBJECT);
            return null;
        }
        if(expression instanceof UnaryExpr unary&&unary.operator()==TokenType.AMPERSAND)return lvalueAddress(unary.operand());
        CrakenType actual=typeOf(expression);
        if(actual!=null&&actual.isArray())return lvalueAddress(expression);
        if(expression instanceof BinaryExpr binary&&(binary.operator()==TokenType.PLUS||binary.operator()==TokenType.MINUS)) {
            CrakenType left=typeOf(binary.left()),right=typeOf(binary.right());
            Expression pointer=binary.left(),index=binary.right();CrakenType type=left;
            if((left==null||!left.isPointer()&&!left.isArray())&&binary.operator()==TokenType.PLUS) {
                pointer=binary.right();index=binary.left();type=right;
            }
            if(type==null||!type.isPointer()&&!type.isArray())return null;
            AddressValue base=addressValue(pointer);if(base==null)return null;
            CrakenType element=type.isArray()?type.elementType():type.pointee();
            long offset=Math.multiplyExact(integerValue(constantValue(index)).longValueExact(),sizeOf(element));
            return base.plus(binary.operator()==TokenType.MINUS?Math.negateExact(offset):offset);
        }
        if(expression instanceof ConditionalExpr selection)
            return addressValue(truth(constantValue(selection.condition()))?selection.thenExpression():selection.elseExpression());
        return null;
    }

    private AddressValue lvalueAddress(Expression expression) {
        if(expression instanceof GroupingExpr group)return lvalueAddress(group.expression());
        if(expression instanceof NameExpr name) {
            CrakenType type=declaredType(name);if(type==null)throw new IllegalArgumentException("Unknown static address: "+name.name());
            return new AddressValue(name.name(),0,type.isFunction()?IrGlobalData.AddressKind.FUNCTION:IrGlobalData.AddressKind.OBJECT);
        }
        if(expression instanceof StringLiteralExpr)return addressValue(expression);
        if(expression instanceof UnaryExpr unary&&unary.operator()==TokenType.STAR)return addressValue(unary.operand());
        if(expression instanceof IndexExpr index) {
            AddressValue base=addressValue(index.target());CrakenType type=typeOf(index.target());
            if(base==null||type==null)return null;
            CrakenType element=type.isArray()?type.elementType():type.isPointer()?type.pointee():null;
            return element==null?null:base.plus(Math.multiplyExact(integerValue(constantValue(index.index())).longValueExact(),sizeOf(element)));
        }
        if(expression instanceof FieldAccessExpr field) {
            AddressValue base=field.viaPointer()?addressValue(field.target()):lvalueAddress(field.target());
            CrakenType type=typeOf(field.target());if(base==null||type==null)return null;
            if(field.viaPointer())type=type.pointee();
            if(!(type.unqualified() instanceof CrakenType.StructType record))return null;
            var member=layout(record.name()).field(field.fieldName()).orElseThrow();
            return base.plus(member.offset());
        }
        return null;
    }

    private CrakenType declaredType(NameExpr name) {
        // A function designator may already have its converted pointer expression type.
        // Relocation identity comes from the declaration, not that value conversion.
        CrakenType declared=symbols.get(name.name());
        return declared!=null?declared:expressionTypes.get(name);
    }

    private CrakenType typeOf(Expression expression) {
        CrakenType known=expressionTypes.get(expression);if(known!=null)return known;
        if(expression instanceof NameExpr name)return symbols.get(name.name());
        if(expression instanceof CastExpr cast)return cast.targetType();
        if(expression instanceof GroupingExpr group)return typeOf(group.expression());
        if(expression instanceof IntegerLiteralExpr)return CrakenType.INT;
        if(expression instanceof LongLiteralExpr)return CrakenType.LONG;
        if(expression instanceof IntegerConstantExpr literal)return literal.type();
        if(expression instanceof BoolLiteralExpr)return CrakenType.BOOL;
        if(expression instanceof CharLiteralExpr)return CrakenType.INT;
        if(expression instanceof FloatLiteralExpr)return CrakenType.FLOAT;
        if(expression instanceof DoubleLiteralExpr literal)return literal.literalType();
        if(expression instanceof NullLiteralExpr)return CrakenType.VOID.pointerTo();
        if(expression instanceof SizeofExpr||expression instanceof AlignofExpr)return CrakenType.UNSIGNED_LONG_LONG;
        if(expression instanceof UnaryExpr unary) {
            CrakenType type=typeOf(unary.operand());
            if(type!=null&&unary.operator()==TokenType.AMPERSAND)return type.pointerTo();
            if(type!=null&&type.isPointer()&&unary.operator()==TokenType.STAR)return type.pointee();
            if(type!=null&&type.isScalar())return unary.operator()==TokenType.BANG?CrakenType.INT:promote(type);
        }
        if(expression instanceof BinaryExpr binary) {
            CrakenType left=typeOf(binary.left()),right=typeOf(binary.right());
            if(left==null||right==null)return null;
            return switch(binary.operator()) {
                case EQUAL_EQUAL,BANG_EQUAL,LESS,LESS_EQUAL,GREATER,GREATER_EQUAL,AMPERSAND_AMPERSAND,PIPE_PIPE -> CrakenType.INT;
                case LESS_LESS,GREATER_GREATER -> promote(left);
                default -> left.isScalar()&&right.isScalar()?commonType(left,right):null;
            };
        }
        if(expression instanceof ConditionalExpr selection) {
            CrakenType left=typeOf(selection.thenExpression()),right=typeOf(selection.elseExpression());
            if(left==null||right==null)return null;
            return left.isScalar()&&right.isScalar()?commonType(left,right):left.equals(right)?left:null;
        }
        if(expression instanceof IndexExpr index) {
            CrakenType type=typeOf(index.target());return type==null?null:type.isArray()?type.elementType():type.isPointer()?type.pointee():null;
        }
        if(expression instanceof FieldAccessExpr field) {
            CrakenType type=typeOf(field.target());if(type==null)return null;if(field.viaPointer())type=type.pointee();
            return type.unqualified() instanceof CrakenType.StructType record?layout(record.name()).field(field.fieldName()).orElseThrow().type():null;
        }
        return null;
    }

    private record ConstantValue(Number value,CrakenType type) {}

    private ConstantValue constantValue(Expression expression) {
        ConstantValue result=switch(expression) {
            case BoolLiteralExpr value -> new ConstantValue(value.value()?1L:0L,CrakenType.BOOL);
            case CharLiteralExpr value -> new ConstantValue((long)value.value(),CrakenType.INT);
            case IntegerLiteralExpr value -> new ConstantValue((long)value.value(),CrakenType.INT);
            case IntegerConstantExpr value -> new ConstantValue(value.value(),value.type());
            case LongLiteralExpr value -> new ConstantValue(value.value(),CrakenType.LONG);
            case FloatLiteralExpr value -> new ConstantValue(value.value(),CrakenType.FLOAT);
            case DoubleLiteralExpr value -> new ConstantValue(value.value(),value.literalType());
            case NullLiteralExpr ignored -> new ConstantValue(0L,CrakenType.VOID.pointerTo());
            case SizeofExpr query -> new ConstantValue((long)sizeOf(queriedType(query.queriedType(),query.expression())),CrakenType.UNSIGNED_LONG_LONG);
            case AlignofExpr query -> new ConstantValue((long)alignmentOf(queriedType(query.queriedType(),query.expression())),CrakenType.UNSIGNED_LONG_LONG);
            case GroupingExpr group -> constantValue(group.expression());
            case CastExpr cast -> convert(constantValue(cast.operand()),cast.targetType());
            case CommaExpr comma -> {
                ConstantValue last=null;
                for(Expression item:comma.expressions())last=constantValue(item);
                if(last==null)throw new IllegalArgumentException("Empty constant comma expression");
                yield last;
            }
            case ConditionalExpr selection -> constantValue(truth(constantValue(selection.condition()))
                    ?selection.thenExpression():selection.elseExpression());
            case UnaryExpr unary -> constantUnary(unary);
            case BinaryExpr binary -> constantBinary(binary);
            default -> throw new IllegalArgumentException("global initializer is not a constant expression: "+expression.getClass().getSimpleName());
        };
        CrakenType type=typeOf(expression);
        return type==null?result:convert(result,type);
    }

    private ConstantValue constantUnary(UnaryExpr expression) {
        ConstantValue value=constantValue(expression.operand());
        if(expression.operator()==TokenType.BANG)return new ConstantValue(truth(value)?0L:1L,CrakenType.INT);
        if(value.type().isIntegerScalar()) {
            var evaluated=TemplateValues.evaluate(new UnaryExpr(expression.operator(),literal(value),expression.range()));
            return new ConstantValue(evaluated.value(),evaluated.type());
        }
        if(value.type().isFloatingScalar())return switch(expression.operator()) {
            case PLUS -> value;
            case MINUS -> convert(new ConstantValue(-value.value().doubleValue(),CrakenType.DOUBLE),value.type());
            default -> throw new IllegalArgumentException("Invalid floating constant unary operator: "+expression.operator());
        };
        throw new IllegalArgumentException("Invalid constant unary operator: "+expression.operator());
    }

    private ConstantValue constantBinary(BinaryExpr expression) {
        ConstantValue left=constantValue(expression.left());
        if(expression.operator()==TokenType.AMPERSAND_AMPERSAND&&!truth(left))return new ConstantValue(0L,CrakenType.INT);
        if(expression.operator()==TokenType.PIPE_PIPE&&truth(left))return new ConstantValue(1L,CrakenType.INT);
        ConstantValue right=constantValue(expression.right());
        if(expression.operator()==TokenType.AMPERSAND_AMPERSAND||expression.operator()==TokenType.PIPE_PIPE)
            return new ConstantValue(truth(right)?1L:0L,CrakenType.INT);
        if(left.type().isIntegerScalar()&&right.type().isIntegerScalar()) {
            var evaluated=TemplateValues.evaluate(new BinaryExpr(literal(left),expression.operator(),literal(right),expression.range()));
            return new ConstantValue(evaluated.value(),evaluated.type());
        }
        CrakenType common=commonType(left.type(),right.type());
        if(!common.isFloatingScalar())throw new IllegalArgumentException("Non-arithmetic constant binary operands");
        double a=convert(left,common).value().doubleValue(),b=convert(right,common).value().doubleValue();
        Boolean comparison=switch(expression.operator()) {
            case EQUAL_EQUAL -> a==b;case BANG_EQUAL -> a!=b;case LESS -> a<b;case LESS_EQUAL -> a<=b;
            case GREATER -> a>b;case GREATER_EQUAL -> a>=b;default -> null;
        };
        if(comparison!=null)return new ConstantValue(comparison?1L:0L,CrakenType.INT);
        double result=switch(expression.operator()) {
            case PLUS -> a+b;case MINUS -> a-b;case STAR -> a*b;case SLASH -> a/b;
            default -> throw new IllegalArgumentException("Invalid floating constant binary operator: "+expression.operator());
        };
        return convert(new ConstantValue(result,CrakenType.DOUBLE),common);
    }

    private static IntegerConstantExpr literal(ConstantValue value) {
        return new IntegerConstantExpr(value.value().longValue(),value.type(),"",CONSTANT_RANGE);
    }

    private static boolean truth(ConstantValue value) {
        return value.type().isFloatingScalar()?value.value().doubleValue()!=0:value.value().longValue()!=0;
    }

    private static java.math.BigInteger integerValue(ConstantValue value) {
        if(!value.type().isIntegerScalar()&&!value.type().isPointer())throw new IllegalArgumentException("Expected integral address offset");
        long bits=value.value().longValue();
        return value.type().isUnsignedIntegerScalar()&&TypeLayout.sizeOf(value.type())==Long.BYTES
                ?new java.math.BigInteger(Long.toUnsignedString(bits)):java.math.BigInteger.valueOf(bits);
    }

    private static ConstantValue convert(ConstantValue value,CrakenType target) {
        target=target.unqualified();
        if(target.equals(CrakenType.BOOL))return new ConstantValue(truth(value)?1L:0L,target);
        if(target.isFloatingScalar()) {
            double number=value.type().isFloatingScalar()?value.value().doubleValue():integerValue(value).doubleValue();
            if(target.equals(CrakenType.FLOAT))return new ConstantValue((float)number,target);
            return new ConstantValue(number,target);
        }
        if(target.isIntegerScalar()) {
            long bits;
            if(value.type().isFloatingScalar()) {
                double number=value.value().doubleValue();
                if(!Double.isFinite(number))throw new IllegalArgumentException("Non-finite floating to integer constant conversion");
                java.math.BigInteger integer=new java.math.BigDecimal(number).toBigInteger();
                int width=TypeLayout.sizeOf(target)*Byte.SIZE;
                java.math.BigInteger min=target.isSignedIntegerScalar()?java.math.BigInteger.ONE.shiftLeft(width-1).negate():java.math.BigInteger.ZERO;
                java.math.BigInteger max=java.math.BigInteger.ONE.shiftLeft(target.isSignedIntegerScalar()?width-1:width).subtract(java.math.BigInteger.ONE);
                if(integer.compareTo(min)<0||integer.compareTo(max)>0)throw new IllegalArgumentException("Floating to integer constant is out of range");
                bits=integer.longValue();
            } else bits=value.value().longValue();
            CrakenType source=value.type().isIntegerScalar()?value.type():CrakenType.UNSIGNED_LONG_LONG;
            var converted=TemplateValues.convert(new TemplateArgument.Integral(bits,source),target,false);
            return new ConstantValue(converted.value(),target);
        }
        if(target.isPointer()||target.isVaList()||target.isNullPointer())return new ConstantValue(value.value().longValue(),target);
        throw new IllegalArgumentException("Non-scalar constant conversion: "+target);
    }

    private static CrakenType promote(CrakenType type) {
        if(type.isIntegerScalar()&&((CrakenType.ScalarType)type.unqualified()).kind().integerRank()<3)return CrakenType.INT;
        return type.unqualified();
    }

    private static CrakenType commonType(CrakenType left,CrakenType right) {
        if(left.unqualified().equals(CrakenType.LONG_DOUBLE)||right.unqualified().equals(CrakenType.LONG_DOUBLE))return CrakenType.LONG_DOUBLE;
        if(left.unqualified().equals(CrakenType.DOUBLE)||right.unqualified().equals(CrakenType.DOUBLE))return CrakenType.DOUBLE;
        if(left.unqualified().equals(CrakenType.FLOAT)||right.unqualified().equals(CrakenType.FLOAT))return CrakenType.FLOAT;
        if(!left.isIntegerScalar()||!right.isIntegerScalar())throw new IllegalArgumentException("Expected arithmetic constant operands");
        return TemplateValues.evaluate(new BinaryExpr(literal(new ConstantValue(0L,left.unqualified())),TokenType.PLUS,
                literal(new ConstantValue(0L,right.unqualified())),CONSTANT_RANGE)).type();
    }

    private CrakenType queriedType(CrakenType explicitType, Expression operand) {
        if (explicitType != null) return explicitType;
        CrakenType type = expressionTypes.get(operand);
        if (type == null) throw new IllegalArgumentException("missing semantic type for global type-query operand");
        return type;
    }

    private int sizeOf(CrakenType type) {
        type = type.unqualified();
        if (type instanceof CrakenType.ArrayType array) return Math.multiplyExact(sizeOf(array.elementType()), array.length());
        if (type instanceof CrakenType.StructType struct) return layout(struct.name()).size();
        return TypeLayout.sizeOf(type);
    }
    private int alignmentOf(CrakenType type) {
        type = type.unqualified();
        if (type instanceof CrakenType.ArrayType array) return alignmentOf(array.elementType());
        if (type instanceof CrakenType.StructType struct) return layout(struct.name()).alignment();
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
