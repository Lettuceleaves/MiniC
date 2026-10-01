package minic.compiler.type;

import minic.compiler.lexer.token.TokenType;
import minic.compiler.parser.node.*;
import minic.compiler.parser.node.Expression.*;
import java.math.BigInteger;
import java.util.Map;

/** Integer constant evaluation for template arguments/bounds. No execution or library-specific rules. */
public final class TemplateValues {
    private TemplateValues() {}
    public static Expression substitute(Expression expression, Map<MiniType.TemplateParameterType,MiniType> types,
                                         Map<MiniType.TemplateParameterType,Expression> values) {
        return switch (expression) {
            case CppTemplateValueExpr value -> values.getOrDefault(value.parameter(),
                    new CppTemplateValueExpr(value.parameter(), value.valueType().substituteTemplateParameters(types), value.range()));
            case CppNoexceptExpr query -> new CppNoexceptExpr(substitute(query.operand(),types,values),query.range());
            case CppTypeQueryExpr query -> new CppTypeQueryExpr(query.kind(), query.arguments().stream()
                    .map(argument -> new CppTypeQueryExpr.TypeArgument(argument.type().substituteTemplateParameters(types, values), argument.packExpansion(), argument.range())).toList(),
                    query.nameRange(), query.range());
            case CppTypeMemberExpr member -> new CppTypeMemberExpr(member.ownerType().substituteTemplateParameters(types,values),member.memberName(),member.nameRange(),member.range());
            case GroupingExpr group -> new GroupingExpr(substitute(group.expression(),types,values),group.range());
            case UnaryExpr unary -> new UnaryExpr(unary.operator(),substitute(unary.operand(),types,values),unary.range());
            case BinaryExpr binary -> new BinaryExpr(substitute(binary.left(),types,values),binary.operator(),substitute(binary.right(),types,values),binary.range());
            case ConditionalExpr conditional -> new ConditionalExpr(substitute(conditional.condition(),types,values),substitute(conditional.thenExpression(),types,values),substitute(conditional.elseExpression(),types,values),conditional.range());
            case CastExpr cast -> new CastExpr(cast.targetType().substituteTemplateParameters(types),substitute(cast.operand(),types,values),cast.range());
            case AlignofExpr align -> new AlignofExpr(align.expression()==null?null:substitute(align.expression(),types,values),align.queriedType()==null?null:align.queriedType().substituteTemplateParameters(types),align.range());
            case SizeofExpr size -> new SizeofExpr(size.expression()==null?null:substitute(size.expression(),types,values),size.queriedType()==null?null:size.queriedType().substituteTemplateParameters(types),size.range());
            default -> expression;
        };
    }
    public static boolean dependent(Expression expression) {
        if (expression instanceof CppTemplateValueExpr || expression instanceof CppSizeofPackExpr || expression instanceof CppPackExpansionExpr) return true;
        if (expression instanceof CppTypeQueryExpr query && query.arguments().stream().anyMatch(argument -> argument.packExpansion() || argument.type().isDependentTemplate())) return true;
        if (expression instanceof AlignofExpr align && align.queriedType()!=null && align.queriedType().isDependentTemplate()) return true;
        if (expression instanceof CppTypeMemberExpr member && member.ownerType().isDependentTemplate()) return true;
        if (expression instanceof SizeofExpr size && size.queriedType()!=null && size.queriedType().isDependentTemplate()) return true;
        for (AstNode child : AstChildren.of(expression)) if (child instanceof Expression e && dependent(e)) return true;
        return false;
    }
    /** Record layout and member constants are resolved by the owning semantic context. */
    public static boolean requiresSemanticContext(Expression expression) {
        if(expression instanceof CppNoexceptExpr||expression instanceof CppTypeQueryExpr||expression instanceof SizeofExpr||expression instanceof AlignofExpr||expression instanceof CppTypeMemberExpr)return true;
        for(AstNode child:AstChildren.of(expression))if(child instanceof Expression e&&requiresSemanticContext(e))return true;
        return false;
    }
    public static TemplateArgument.Integral evaluate(Expression expression) {
        return switch (expression) {
            case GroupingExpr group -> evaluate(group.expression());
            case IntegerLiteralExpr literal -> new TemplateArgument.Integral(literal.value(),MiniType.INT);
            case LongLiteralExpr literal -> new TemplateArgument.Integral(literal.value(),MiniType.LONG);
            case IntegerConstantExpr literal -> new TemplateArgument.Integral(literal.value(),literal.type());
            case BoolLiteralExpr literal -> new TemplateArgument.Integral(literal.value()?1:0,MiniType.BOOL);
            case CharLiteralExpr literal -> new TemplateArgument.Integral(literal.value(),MiniType.INT);
            case SizeofExpr size when size.queriedType()!=null -> new TemplateArgument.Integral(TypeLayout.sizeOf(size.queriedType()),MiniType.UNSIGNED_LONG_LONG);
            case AlignofExpr align when align.queriedType()!=null -> new TemplateArgument.Integral(TypeLayout.alignmentOf(align.queriedType()),MiniType.UNSIGNED_LONG_LONG);
            case CastExpr cast -> convert(evaluate(cast.operand()),cast.targetType(),false);
            case UnaryExpr unary -> unary(unary);
            case BinaryExpr binary -> binary(binary);
            case ConditionalExpr conditional -> evaluate(evaluate(conditional.condition()).value()!=0?conditional.thenExpression():conditional.elseExpression());
            default -> throw new IllegalArgumentException("Expected an integral constant expression");
        };
    }
    public static TemplateArgument.Integral convert(TemplateArgument.Integral value, MiniType target, boolean requireRepresentable) {
        if (!target.isIntegerScalar()) throw new IllegalArgumentException("Non-type template argument requires an integral parameter type");
        BigInteger integer=number(value);
        if (target.unqualified().equals(MiniType.BOOL)) {
            if (requireRepresentable && !integer.equals(BigInteger.ZERO) && !integer.equals(BigInteger.ONE))
                throw new IllegalArgumentException("Narrowing non-type template argument to bool");
            return new TemplateArgument.Integral(integer.signum()==0?0:1,target.unqualified());
        }
        return checked(integer,target.unqualified(),requireRepresentable);
    }
    private static TemplateArgument.Integral unary(UnaryExpr unary) {
        var value=evaluate(unary.operand());
        MiniType type=promote(value.type());
        value=convert(value,type,false);
        return switch(unary.operator()) {
            case PLUS -> value;
            case MINUS -> checked(number(value).negate(),type,type.isSignedIntegerScalar());
            case TILDE -> checked(number(value).not(),type,false);
            case BANG -> new TemplateArgument.Integral(value.value()==0?1:0,MiniType.BOOL);
            default -> throw new IllegalArgumentException("Not a constant unary operator");
        };
    }
    private static TemplateArgument.Integral binary(BinaryExpr binary) {
        var left=evaluate(binary.left());
        if(binary.operator()==TokenType.AMPERSAND_AMPERSAND && left.value()==0) return new TemplateArgument.Integral(0,MiniType.BOOL);
        if(binary.operator()==TokenType.PIPE_PIPE && left.value()!=0) return new TemplateArgument.Integral(1,MiniType.BOOL);
        var right=evaluate(binary.right());
        boolean shift=binary.operator()==TokenType.LESS_LESS||binary.operator()==TokenType.GREATER_GREATER;
        MiniType type=shift?promote(left.type()):common(left.type(),right.type());
        BigInteger a=number(convert(left,type,false));
        BigInteger b=number(convert(right,shift?promote(right.type()):type,false));
        int cmp=a.compareTo(b);
        Boolean predicate=switch(binary.operator()) {
            case EQUAL_EQUAL -> cmp==0; case BANG_EQUAL -> cmp!=0; case LESS -> cmp<0; case LESS_EQUAL -> cmp<=0;
            case GREATER -> cmp>0; case GREATER_EQUAL -> cmp>=0;
            case AMPERSAND_AMPERSAND -> a.signum()!=0 && b.signum()!=0; case PIPE_PIPE -> a.signum()!=0 || b.signum()!=0;
            default -> null;
        };
        if(predicate!=null) return new TemplateArgument.Integral(predicate?1:0,MiniType.BOOL);
        if(shift && (b.signum()<0 || b.compareTo(BigInteger.valueOf(width(type)))>=0)) throw new IllegalArgumentException("Invalid constant shift count");
        if((binary.operator()==TokenType.SLASH||binary.operator()==TokenType.PERCENT)&&b.signum()==0) throw new IllegalArgumentException("Division by zero in constant expression");
        BigInteger result=switch(binary.operator()) {
            case PLUS -> a.add(b); case MINUS -> a.subtract(b); case STAR -> a.multiply(b);
            case SLASH -> a.divide(b); case PERCENT -> a.remainder(b);
            case AMPERSAND -> a.and(b); case PIPE -> a.or(b); case CARET -> a.xor(b);
            case LESS_LESS -> a.shiftLeft(b.intValue()); case GREATER_GREATER -> a.shiftRight(b.intValue());
            default -> throw new IllegalArgumentException("Not a constant binary operator");
        };
        return checked(result,type,type.isSignedIntegerScalar());
    }
    private static MiniType promote(MiniType type) {
        if(!type.isIntegerScalar()) throw new IllegalArgumentException("Expected integral type");
        return ((MiniType.ScalarType)type.unqualified()).kind().integerRank()<3?MiniType.INT:type.unqualified();
    }
    private static MiniType common(MiniType a,MiniType b) {
        a=promote(a);b=promote(b);
        var x=((MiniType.ScalarType)a).kind();var y=((MiniType.ScalarType)b).kind();
        if(x.signed()==y.signed()) return x.integerRank()>=y.integerRank()?a:b;
        MiniType unsigned=x.signed()?b:a,signed=x.signed()?a:b;
        var u=((MiniType.ScalarType)unsigned).kind();var s=((MiniType.ScalarType)signed).kind();
        if(u.integerRank()>=s.integerRank())return unsigned;
        if(s.sizeBytes()>u.sizeBytes())return signed;
        return s.integerRank()==5?MiniType.UNSIGNED_LONG_LONG:s.integerRank()==4?MiniType.UNSIGNED_LONG:MiniType.UNSIGNED_INT;
    }
    private static int width(MiniType type) { return ((MiniType.ScalarType)type.unqualified()).kind().sizeBytes()*8; }
    private static BigInteger number(TemplateArgument.Integral value) {
        long bits=value.value();
        if(value.type().isUnsignedIntegerScalar() && width(value.type())==64) return new BigInteger(Long.toUnsignedString(bits));
        return BigInteger.valueOf(bits);
    }
    private static TemplateArgument.Integral checked(BigInteger value,MiniType type,boolean check) {
        int width=width(type);boolean signed=type.isSignedIntegerScalar();
        BigInteger modulus=BigInteger.ONE.shiftLeft(width),max=BigInteger.ONE.shiftLeft(signed?width-1:width).subtract(BigInteger.ONE);
        BigInteger min=signed?BigInteger.ONE.shiftLeft(width-1).negate():BigInteger.ZERO;
        if(check&&(value.compareTo(min)<0||value.compareTo(max)>0))throw new IllegalArgumentException("Integral constant is outside its type's range");
        BigInteger bits=value.mod(modulus);
        if(signed&&bits.testBit(width-1))bits=bits.subtract(modulus);
        return new TemplateArgument.Integral(bits.longValue(),type);
    }
}
