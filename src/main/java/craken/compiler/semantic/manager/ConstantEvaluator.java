package craken.compiler.semantic.manager;

import craken.SourceRange;
import craken.compiler.lexer.token.TokenType;
import craken.compiler.parser.node.*;
import craken.compiler.parser.node.Declaration.*;
import craken.compiler.parser.node.Expression.*;
import craken.compiler.parser.node.Statement.*;
import craken.compiler.type.*;
import java.util.*;

/** Bounded constant evaluation of already-bound core AST. It never executes native code or host I/O. */
public final class ConstantEvaluator {
    public record Limits(int steps,int calls,int objects) {
        public Limits {if(steps<1||calls<1||objects<1)throw new IllegalArgumentException("Positive evaluator limits required");}
        public static Limits defaults(){return new Limits(100_000,128,100_000);}
    }
    public record Global(CrakenType type,Expression initializer,boolean readable,boolean staticStorage) {
        public Global(CrakenType type,Expression initializer,boolean readable){this(type,initializer,readable,true);}
    }
    public record Function(FunctionDecl declaration,boolean constantEligible) { }
    public interface Context {
        CrakenType type(Expression expression);
        Function function(String name);
        Global global(String name);
        List<StructField> fields(CrakenType type);
        int sizeOf(CrakenType type);
        int alignmentOf(CrakenType type);
        default boolean union(CrakenType type){return false;}
        default String staticTemporary(MaterializeExpr temporary){return null;}
        default void initializedTemporary(String name,CrakenType type,Expression initializer){}
        default boolean internalArrayDecay(CastExpr expression,CrakenType from,CrakenType to){return false;}
        default boolean baseConversion(CrakenType from,CrakenType to){return false;}
    }
    public sealed interface Value permits IntegerValue,FloatingValue,PointerValue,FunctionValue,ObjectValue,VoidValue {
        CrakenType type();
    }
    public record IntegerValue(long value,CrakenType type) implements Value { }
    public record FloatingValue(double value,CrakenType type) implements Value { }
    public record PointerValue(Cell base,int offset,CrakenType type) implements Value {
        public boolean isNull(){return base==null;}
    }
    public record FunctionValue(String name,CrakenType type) implements Value { }
    public record ObjectValue(Cell storage) implements Value {public CrakenType type(){return storage.type;}}
    public record VoidValue() implements Value {public CrakenType type(){return CrakenType.VOID;}}
    /** Identity-bearing storage. Constant pointers are locations, never invented machine addresses. */
    public static final class Cell {
        private final CrakenType type;
        private final String globalName;
        private final Cell parent;
        private final int index;
        private final LinkedHashMap<String,Cell> members=new LinkedHashMap<>();
        private Value value;
        private boolean initialized,alive=true,readable=true,mutable=true;
        private Cell(CrakenType type,String globalName,Cell parent,int index){this.type=type;this.globalName=globalName;this.parent=parent;this.index=index;}
        public CrakenType type(){return type;}
        public String globalName(){return globalName;}
        public Map<String,Cell> members(){return Collections.unmodifiableMap(members);}
    }
    public static final class Failure extends IllegalArgumentException {
        private final SourceRange range;
        public Failure(SourceRange range,String message){super(message);this.range=range;}
        public SourceRange range(){return range;}
    }
    private static final class Frame {
        final Frame parent;
        final Map<String,Cell> names=new LinkedHashMap<>();
        final List<Cell> owned=new ArrayList<>();
        final List<Cell> temporaries=new ArrayList<>();
        final Cell returnStorage;
        final boolean expressionFrame;
        final boolean staticLifetime;
        Frame(Frame parent,Cell returnStorage){this(parent,returnStorage,false,false);}
        Frame(Frame parent,Cell returnStorage,boolean expressionFrame){this(parent,returnStorage,expressionFrame,false);}
        Frame(Frame parent,Cell returnStorage,boolean expressionFrame,boolean staticLifetime){this.parent=parent;this.returnStorage=returnStorage;this.expressionFrame=expressionFrame;this.staticLifetime=staticLifetime;}
        Cell local(String name){Cell value=names.get(name);return value!=null?value:parent==null?null:parent.local(name);}
    }
    private enum Flow { NORMAL,RETURN,BREAK,CONTINUE }
    private record Completion(Flow flow,Value value) {
        static Completion normal(){return new Completion(Flow.NORMAL,new VoidValue());}
    }
    private final Context context;
    private final Limits limits;
    private final Map<String,Cell> globals=new HashMap<>();
    private final Set<String> initializing=new HashSet<>();
    private final IdentityHashMap<StringLiteralExpr,Cell> strings=new IdentityHashMap<>();
    private final List<Runnable> publications=new ArrayList<>();
    private int steps,calls,objects;
    public ConstantEvaluator(Context context,Limits limits){this.context=Objects.requireNonNull(context);this.limits=Objects.requireNonNull(limits);}
    public Value evaluate(Expression expression){
        reset();Frame frame=new Frame(null,null);Value value=eval(expression,frame);validateResult(value,expression.range(),new HashSet<>());publish();return value;
    }
    /** Evaluates a constexpr object's initializer in its final symbolic storage. */
    public Value initialize(String name,CrakenType type,Expression initializer){return initialize(name,type,initializer,true);}
    public Value initialize(String name,CrakenType type,Expression initializer,boolean staticStorage){
        reset();Cell cell=allocate(type,staticStorage?name:null,null,-1,initializer.range());globals.put(name,cell);initializing.add(name);
        try {initializeAt(cell,initializer,new Frame(null,null,false,staticStorage));freeze(cell,true);Value result=read(cell,initializer.range());validateResult(result,initializer.range(),new HashSet<>());publish();return result;}
        catch(Failure failure){globals.remove(name);throw failure;}
        finally{initializing.remove(name);}
    }
    public static Expression scalarExpression(Value value,SourceRange range){
        return switch(value){
            case IntegerValue integer -> new IntegerConstantExpr(integer.value,integer.type,Long.toString(integer.value),range);
            case FloatingValue number -> number.type.unqualified().equals(CrakenType.FLOAT)
                    ?new FloatLiteralExpr((float)number.value,Float.toString((float)number.value),range)
                    :new DoubleLiteralExpr(number.value,Double.toString(number.value)+(number.type.unqualified().equals(CrakenType.LONG_DOUBLE)?"L":""),range);
            case PointerValue pointer when pointer.isNull() -> new CastExpr(pointer.type,new IntegerLiteralExpr(0,"0",range),range);
            default -> throw new Failure(range,"The constant result is not a scalar literal");
        };
    }
    /** Serializes a proven value; pointer results retain symbol identity for data relocation. */
    public Expression constantExpression(Value value,SourceRange range){
        if(value instanceof ObjectValue object){
            List<Expression> elements=new ArrayList<>();
            for(Cell member:object.storage.members.values())elements.add(constantExpression(read(member,range),range));
            return new AggregateInitExpr(elements,range);
        }
        if(value instanceof FunctionValue function)return new NameExpr(function.name,range);
        if(value instanceof PointerValue pointer&&!pointer.isNull()){
            Cell storage=pointer.base;Expression target=storageExpression(storage,range);
            Expression address;
            if(storage.type.isArray()&&!wholeObjectPointer(pointer))address=new UnaryExpr(TokenType.AMPERSAND,new IndexExpr(target,new IntegerLiteralExpr(0,"0",range),range),range);
            else address=new UnaryExpr(TokenType.AMPERSAND,target,range);
            if(pointer.offset!=0)address=new BinaryExpr(address,TokenType.PLUS,new IntegerLiteralExpr(pointer.offset,Integer.toString(pointer.offset),range),range);
            return new CastExpr(pointer.type,address,range);
        }
        return scalarExpression(value,range);
    }
    private Expression storageExpression(Cell storage,SourceRange range){
        if(storage.parent!=null){
            Expression owner=storageExpression(storage.parent,range);
            if(storage.parent.type.isArray())return new IndexExpr(owner,new IntegerLiteralExpr(storage.index,Integer.toString(storage.index),range),range);
            for(var member:storage.parent.members.entrySet())if(member.getValue()==storage){
                if(member.getKey().startsWith("$anonymous$"))throw fail(range,"An anonymous subobject address has no serializable field designator");
                return new FieldAccessExpr(owner,member.getKey(),false,range);
            }
        }
        if(storage.globalName==null)throw fail(range,"A constant address cannot refer to automatic storage");
        for(var literal:strings.entrySet())if(literal.getValue()==storage)return literal.getKey();
        return new NameExpr(storage.globalName,range);
    }
    private void reset(){steps=0;calls=0;objects=0;publications.clear();}
    private void publish(){List<Runnable> completed=List.copyOf(publications);publications.clear();completed.forEach(Runnable::run);}
    private void tick(SourceRange range){if(++steps>limits.steps)throw fail(range,"Constant evaluation step limit exceeded");}
    private Failure fail(SourceRange range,String message){return new Failure(range,message);}
    private CrakenType type(Expression expression){CrakenType type=context.type(expression);if(type==null)throw fail(expression.range(),"Constant expression type is unavailable");return type;}
    private Value eval(Expression node,Frame frame){
        if(node==null)return new VoidValue();tick(node.range());
        return switch(node){
            case IntegerLiteralExpr n -> new IntegerValue(n.value(),CrakenType.INT);
            case IntegerConstantExpr n -> new IntegerValue(n.value(),n.type());
            case LongLiteralExpr n -> new IntegerValue(n.value(),context.type(n)==null?CrakenType.LONG_LONG:context.type(n));
            case StringLiteralExpr n -> new ObjectValue(string(n));
            case BoolLiteralExpr n -> new IntegerValue(n.value()?1:0,CrakenType.BOOL);
            case CharLiteralExpr n -> new IntegerValue(n.value(),CrakenType.CHAR);
            case FloatLiteralExpr n -> new FloatingValue(n.value(),CrakenType.FLOAT);
            case DoubleLiteralExpr n -> new FloatingValue(n.value(),n.literalType());
            case NullLiteralExpr n -> new PointerValue(null,0,CrakenType.NULL);
            case NameExpr n -> {
                Cell cell=frame.local(n.name());
                if(cell==null&&context.function(n.name())!=null)yield new FunctionValue(n.name(),type(n));
                yield read(cell==null?global(n.name(),n.range()):cell,n.range());
            }
            case GroupingExpr n -> eval(n.expression(),frame);
            case UnaryExpr n -> unary(n,frame);
            case PostfixUpdateExpr n -> update(n.target(),n.operator(),true,frame,n.range());
            case BinaryExpr n -> binary(n,frame);
            case ConditionalExpr n -> eval(truth(eval(n.condition(),frame),n.range())?n.thenExpression():n.elseExpression(),frame);
            case CastExpr n -> {
                Value operand=eval(n.operand(),frame);
                if(operand instanceof PointerValue pointer&&pointer.type.isPointer()&&n.targetType().isPointer()
                        &&!similarObjectType(pointer.type.pointee(),n.targetType().pointee())
                        &&!n.targetType().pointee().isVoid()&&!context.baseConversion(pointer.type.pointee(),n.targetType().pointee())&&!context.internalArrayDecay(n,pointer.type,n.targetType()))
                    throw fail(n.range(),"A reinterpretation or void-pointer downcast is not a constant expression");
                yield convert(operand,n.targetType(),n.range());
            }
            case FieldAccessExpr n -> read(lvalue(n,frame),n.range());
            case IndexExpr n -> read(lvalue(n,frame),n.range());
            case AssignmentExpr n -> {
                Value value=eval(n.value(),frame);Cell target=lvalue(n.target(),frame);
                if(n.compoundBinaryOperator().isPresent())value=operation(read(target,n.range()),n.compoundBinaryOperator().orElseThrow(),value,n.range());
                store(target,convert(value,target.type,n.range()),false,n.range());yield read(target,n.range());
            }
            case InitializeExpr n -> {Cell target=lvalue(n.target(),frame);initializeAt(target,n.value(),frame);yield new VoidValue();}
            case CommaExpr n -> {Value value=new VoidValue();for(Expression item:n.expressions())value=eval(item,frame);yield value;}
            case LetExpr n -> {
                Value value=eval(n.initializer(),frame);Frame nested=new Frame(frame,frame.returnStorage,true);
                Cell cell=allocate(n.type(),null,null,-1,n.range());store(cell,convert(value,n.type(),n.range()),true,n.range());cell.mutable=false;nested.names.put(n.name(),cell);
                Value result=eval(n.body(),nested);frame.temporaries.addAll(nested.temporaries);yield result;
            }
            case ObjectInitExpr n -> {Cell cell=allocate(n.type(),null,null,-1,n.range());frame.temporaries.add(cell);initializeAt(cell,n,frame);yield new ObjectValue(cell);}
            case MaterializeExpr n -> {
                boolean scoped=n.lifetime().kind()==TemporaryLifetime.Kind.REFERENCE_SCOPE;
                String global=scoped&&scope(frame).staticLifetime?context.staticTemporary(n):null;
                Cell cell=allocate(n.type(),global,null,-1,n.range());
                if(global==null)(scoped?scope(frame).owned:frame.temporaries).add(cell);
                initializeAt(cell,n.initializer(),frame);
                if(global!=null){globals.put(global,cell);Expression value=constantExpression(read(cell,n.range()),n.range());publications.add(()->context.initializedTemporary(global,n.type(),value));}
                yield pointer(cell,n.type().pointerTo());
            }
            case CleanupExpr n -> {Value value=eval(n.value(),frame);eval(n.cleanup(),frame);yield value;}
            case SizeofExpr n -> new IntegerValue(context.sizeOf(n.queriedType()!=null?n.queriedType():type(n.expression())),CrakenType.UNSIGNED_LONG_LONG);
            case AlignofExpr n -> new IntegerValue(context.alignmentOf(n.queriedType()!=null?n.queriedType():type(n.expression())),CrakenType.UNSIGNED_LONG_LONG);
            case CallExpr n -> invoke(n,frame,null);
            default -> throw fail(node.range(),"This operation is not permitted in a constant expression: "+node.getClass().getSimpleName());
        };
    }
    private Cell string(StringLiteralExpr literal){
        Cell existing=strings.get(literal);if(existing!=null)return existing;
        int[] units=switch(literal.encoding()){
            case ORDINARY,UTF8 -> {byte[] bytes=literal.value().getBytes(java.nio.charset.StandardCharsets.UTF_8);int[] values=new int[bytes.length];for(int i=0;i<bytes.length;i++)values[i]=bytes[i]&255;yield values;}
            case UTF16 -> literal.value().chars().toArray();
            case UTF32 -> literal.value().codePoints().toArray();
        };
        CrakenType element=switch(literal.encoding()){case ORDINARY,UTF8->CrakenType.CHAR;case UTF16->CrakenType.UNSIGNED_SHORT;case UTF32->CrakenType.UNSIGNED_INT;};
        Cell cell=allocate(CrakenType.qualified(element, Set.of(CrakenType.TypeQualifier.CONST)).arrayOf(units.length+1),"$string$"+strings.size(),null,-1,literal.range());
        int i=0;for(Cell item:cell.members.values())store(item,convert(new IntegerValue(i<units.length?units[i++]:0,CrakenType.INT),element,literal.range()),true,literal.range());
        cell.initialized=true;freeze(cell,true);strings.put(literal,cell);return cell;
    }
    private Cell global(String name,SourceRange range){
        Cell old=globals.get(name);if(old!=null)return old;
        Global source=context.global(name);if(source==null)throw fail(range,"Unknown constant object: "+name);
        Cell cell=allocate(source.type,source.staticStorage?name:null,null,-1,range);globals.put(name,cell);cell.readable=source.readable;
        if(source.readable){
            if(source.initializer==null)throw fail(range,"Constant object has no initializer: "+name);
            if(!initializing.add(name))throw fail(range,"Constant initialization is recursive: "+name);
            try{initializeAt(cell,source.initializer,new Frame(null,null,false,source.staticStorage));freeze(cell,true);}catch(Failure invalid){globals.remove(name);throw invalid;}finally{initializing.remove(name);}
        }else freeze(cell,false);
        return cell;
    }
    private Cell allocate(CrakenType type,String global,Cell parent,int index,SourceRange range){
        tick(range);if(++objects>limits.objects)throw fail(range,"Constant evaluation object limit exceeded");
        Cell cell=new Cell(type,global,parent,index);
        if(type.isArray()){
            if(type.arrayLength()<0)throw fail(range,"An incomplete array cannot be constructed in a constant expression");
            for(int i=0;i<type.arrayLength();i++)cell.members.put(Integer.toString(i),allocate(type.elementType(),global,cell,i,range));
        }else if(type.isStruct()){
            if(context.union(type))throw fail(range,"Active union member constant evaluation is not supported yet");
            int anonymous=0;
            for(StructField field:context.fields(type)){
                String name=field.anonymous()?"$anonymous$"+anonymous++:field.name();
                cell.members.put(name,allocate(field.type(),global,cell,-1,field.range()));
            }
        }
        return cell;
    }
    private void freeze(Cell cell,boolean readable){cell.mutable=false;cell.readable=readable;for(Cell member:cell.members.values())freeze(member,readable);}
    private void expire(Cell cell){cell.alive=false;cell.members.values().forEach(this::expire);}
    private Frame scope(Frame frame){return frame.expressionFrame&&frame.parent!=null?scope(frame.parent):frame;}
    private void close(Frame frame){frame.owned.forEach(this::expire);endExpression(frame);}
    private void endExpression(Frame frame){frame.temporaries.forEach(this::expire);frame.temporaries.clear();}
    private Value fullExpression(Expression expression,Frame frame){try{return eval(expression,frame);}finally{endExpression(frame);}}
    private void accessible(Cell cell,SourceRange range){if(!cell.alive)throw fail(range,"Constant expression accesses an object outside its lifetime");}
    private Value read(Cell cell,SourceRange range){
        accessible(cell,range);
        // An aggregate designator does not read its stored value. Member reads and copies check each subobject.
        if(cell.type.isArray()||cell.type.isStruct())return new ObjectValue(cell);
        if(!cell.readable||cell.type.isVolatileQualified())throw fail(range,"Reading this object is not permitted in a constant expression");
        if(!cell.initialized)throw fail(range,"Reading an uninitialized object in a constant expression");return cell.value;
    }
    private void store(Cell target,Value value,boolean initialization,SourceRange range){
        accessible(target,range);
        if(!target.mutable||!initialization&&target.type.isConstQualified())
            throw fail(range,"Constant expression attempts to modify an object whose lifetime did not begin in this evaluation, or a const object");
        if(value instanceof ObjectValue source){copy(target,source.storage,range);return;}
        target.value=value;target.initialized=true;
    }
    private void copy(Cell target,Cell source,SourceRange range){
        accessible(source,range);
        if(target.members.size()!=source.members.size())throw fail(range,"Constant aggregate copy has mismatched types");
        if(!target.members.isEmpty()){
            var from=source.members.values().iterator();for(Cell member:target.members.values())copy(member,from.next(),range);target.initialized=true;
        }else if(target.type.isStruct()||target.type.isArray()){target.initialized=true;}else{target.value=read(source,range);target.initialized=true;}
    }
    private PointerValue pointer(Cell cell,CrakenType type){return cell.parent!=null&&cell.parent.type.isArray()?new PointerValue(cell.parent,cell.index,type):new PointerValue(cell,0,type);}
    private Cell dereference(PointerValue pointer,SourceRange range){
        if(pointer.base==null)throw fail(range,"Null pointer dereference in a constant expression");
        accessible(pointer.base,range);
        if(pointer.base.type.isArray()&&!wholeObjectPointer(pointer)){
            if(pointer.offset<0||pointer.offset>=pointer.base.members.size())throw fail(range,"Pointer is outside its array object");
            return new ArrayList<>(pointer.base.members.values()).get(pointer.offset);
        }
        if(pointer.offset!=0)throw fail(range,"Dereferencing a one-past pointer in a constant expression");return pointer.base;
    }
    private Cell lvalue(Expression expression,Frame frame){
        tick(expression.range());
        return switch(expression){
            case NameExpr n -> {Cell local=frame.local(n.name());yield local!=null?local:global(n.name(),n.range());}
            case GroupingExpr n -> lvalue(n.expression(),frame);
            case UnaryExpr n when n.operator()==TokenType.STAR -> {
                Value address=eval(n.operand(),frame);if(!(address instanceof PointerValue pointer))throw fail(n.range(),"Dereference requires an object pointer");yield dereference(pointer,n.range());
            }
            case IndexExpr n -> {
                Value base=eval(n.target(),frame);long index=integer(eval(n.index(),frame),n.range());
                PointerValue address=indexBase(base,type(n).pointerTo(),n.range());
                yield dereference(offset(address,index,n.range()),n.range());
            }
            case FieldAccessExpr n -> {
                Value receiver=eval(n.target(),frame);Cell owner=n.viaPointer()?dereference((PointerValue)receiver,n.range()):((ObjectValue)receiver).storage;
                Cell field=field(owner,n.fieldName());if(field==null)throw fail(n.range(),"Unknown constant object member: "+n.fieldName());yield field;
            }
            case ConditionalExpr n -> lvalue(truth(eval(n.condition(),frame),n.range())?n.thenExpression():n.elseExpression(),frame);
            case CommaExpr n -> {for(int i=0;i<n.expressions().size()-1;i++)eval(n.expressions().get(i),frame);yield lvalue(n.expressions().getLast(),frame);}
            default -> {Value value=eval(expression,frame);if(value instanceof ObjectValue object)yield object.storage;throw fail(expression.range(),"Expression does not designate constant-evaluation storage");}
        };
    }
    private Cell field(Cell owner,String name){Cell direct=owner.members.get(name);if(direct!=null)return direct;for(var entry:owner.members.entrySet())if(entry.getKey().startsWith("$anonymous$")){Cell found=field(entry.getValue(),name);if(found!=null)return found;}return null;}
    private Value unary(UnaryExpr expression,Frame frame){
        TokenType operator=expression.operator();SourceRange range=expression.range();
        if(operator==TokenType.AMPERSAND){
            Expression operand=expression.operand();while(operand instanceof GroupingExpr group)operand=group.expression();
            if(operand instanceof NameExpr name&&context.function(name.name())!=null)return new FunctionValue(name.name(),type(expression));
            if(operand instanceof UnaryExpr unary&&unary.operator()==TokenType.STAR){Value address=eval(unary.operand(),frame);if(address instanceof FunctionValue function)return new FunctionValue(function.name,type(expression));if(address instanceof PointerValue pointer)return new PointerValue(pointer.base,pointer.offset,type(expression));}
            if(operand instanceof IndexExpr index){
                Value base=eval(index.target(),frame);long position=integer(eval(index.index(),frame),index.range());
                PointerValue address=indexBase(base,type(expression),index.range());
                PointerValue result=offset(address,position,range);return new PointerValue(result.base,result.offset,type(expression));
            }
            return pointer(lvalue(expression.operand(),frame),type(expression));
        }
        if(operator==TokenType.STAR){Value address=eval(expression.operand(),frame);if(address instanceof FunctionValue)return address;
            if(!(address instanceof PointerValue pointer))throw fail(range,"Dereference requires a pointer");return read(dereference(pointer,range),range);}
        if(operator==TokenType.PLUS_PLUS||operator==TokenType.MINUS_MINUS)return update(expression.operand(),operator,false,frame,range);
        Value value=eval(expression.operand(),frame);
        if(operator==TokenType.BANG)return bool(!truth(value,range));
        if(value instanceof FloatingValue number){
            if(operator!=TokenType.MINUS&&operator!=TokenType.PLUS)throw fail(range,"Invalid floating unary operation");
            return new FloatingValue(operator==TokenType.MINUS?-number.value:number.value,number.type);
        }
        try{var evaluated=TemplateValues.evaluate(new UnaryExpr(operator,scalarExpression(value,range),range));return new IntegerValue(evaluated.value(),evaluated.type());}
        catch(IllegalArgumentException invalid){throw fail(range,invalid.getMessage());}
    }
    private Value update(Expression target,TokenType operator,boolean postfix,Frame frame,SourceRange range){
        Cell cell=lvalue(target,frame);Value previous=read(cell,range);Value updated=convert(operation(previous,operator==TokenType.PLUS_PLUS?TokenType.PLUS:TokenType.MINUS,new IntegerValue(1,CrakenType.INT),range),cell.type,range);
        store(cell,updated,false,range);return postfix?previous:updated;
    }
    private Value binary(BinaryExpr expression,Frame frame){
        Value left=eval(expression.left(),frame);TokenType operator=expression.operator();
        if(operator==TokenType.AMPERSAND_AMPERSAND&&!truth(left,expression.range()))return bool(false);
        if(operator==TokenType.PIPE_PIPE&&truth(left,expression.range()))return bool(true);
        return operation(left,operator,eval(expression.right(),frame),expression.range());
    }
    private Value operation(Value left,TokenType operator,Value right,SourceRange range){
        if(operator==TokenType.AMPERSAND_AMPERSAND)return bool(truth(left,range)&&truth(right,range));
        if(operator==TokenType.PIPE_PIPE)return bool(truth(left,range)||truth(right,range));
        if(left instanceof FunctionValue||right instanceof FunctionValue){
            if(operator!=TokenType.EQUAL_EQUAL&&operator!=TokenType.BANG_EQUAL)throw fail(range,"Only equality is defined for constant function pointers");
            boolean equal=left instanceof FunctionValue a&&right instanceof FunctionValue b&&a.name.equals(b.name);
            return bool(operator==TokenType.EQUAL_EQUAL?equal:!equal);
        }
        if(left instanceof ObjectValue object&&object.type().isArray())left=new PointerValue(object.storage,0,object.type().elementType().pointerTo());
        if(right instanceof ObjectValue object&&object.type().isArray())right=new PointerValue(object.storage,0,object.type().elementType().pointerTo());
        if(left instanceof PointerValue a&&right instanceof IntegerValue b&&(operator==TokenType.PLUS||operator==TokenType.MINUS))return offset(a,operator==TokenType.PLUS?b.value:-b.value,range);
        if(left instanceof IntegerValue a&&right instanceof PointerValue b&&operator==TokenType.PLUS)return offset(b,a.value,range);
        if(left instanceof PointerValue a&&right instanceof PointerValue b){
            boolean equal=a.base==b.base&&a.offset==b.offset;
            if(operator==TokenType.EQUAL_EQUAL||operator==TokenType.BANG_EQUAL)return bool(operator==TokenType.EQUAL_EQUAL?equal:!equal);
            if(a.base!=b.base)throw fail(range,"Unrelated pointers cannot be ordered or subtracted in a constant expression");
            if(operator==TokenType.MINUS)return new IntegerValue(a.offset-b.offset,CrakenType.LONG_LONG);
            return comparison(operator,Integer.compare(a.offset,b.offset),range);
        }
        if(left instanceof FloatingValue||right instanceof FloatingValue){
            double a=number(left,range),b=number(right,range);CrakenType type=TypeCompatibility.usualArithmeticType(left.type(),right.type());
            if(Set.of(TokenType.EQUAL_EQUAL,TokenType.BANG_EQUAL,TokenType.LESS,TokenType.LESS_EQUAL,TokenType.GREATER,TokenType.GREATER_EQUAL).contains(operator))return comparison(operator,a==b?0:a<b?-1:1,range);
            double value=switch(operator){case PLUS->a+b;case MINUS->a-b;case STAR->a*b;case SLASH->a/b;default->throw fail(range,"Invalid floating constant operation");};
            if(type.equals(CrakenType.FLOAT))value=(float)value;
            if(!Double.isFinite(value))throw fail(range,"Floating constant operation is outside its finite range");return new FloatingValue(value,type);
        }
        try{var result=TemplateValues.evaluate(new BinaryExpr(scalarExpression(left,range),operator,scalarExpression(right,range),range));return new IntegerValue(result.value(),result.type());}
        catch(IllegalArgumentException invalid){throw fail(range,invalid.getMessage());}
    }
    private Value comparison(TokenType operator,int comparison,SourceRange range){return bool(switch(operator){case EQUAL_EQUAL->comparison==0;case BANG_EQUAL->comparison!=0;case LESS->comparison<0;case LESS_EQUAL->comparison<=0;case GREATER->comparison>0;case GREATER_EQUAL->comparison>=0;default->throw fail(range,"Invalid constant comparison");});}
    private IntegerValue bool(boolean value){return new IntegerValue(value?1:0,CrakenType.BOOL);}
    private long integer(Value value,SourceRange range){if(value instanceof IntegerValue number)return number.value;throw fail(range,"Integral constant required");}
    private double number(Value value,SourceRange range){if(value instanceof FloatingValue floating)return floating.value;if(value instanceof IntegerValue integer)return integer.type.isUnsignedIntegerScalar()?new java.math.BigInteger(Long.toUnsignedString(integer.value)).doubleValue():integer.value;throw fail(range,"Arithmetic constant required");}
    private boolean truth(Value value,SourceRange range){if(value instanceof PointerValue pointer)return !pointer.isNull();if(value instanceof FunctionValue)return true;return number(value,range)!=0;}
    private PointerValue offset(PointerValue pointer,long delta,SourceRange range){
        if(pointer.base==null&&delta!=0)throw fail(range,"Arithmetic on a null pointer in a constant expression");
        long offset=pointer.offset+delta;int length=pointer.base==null?0:pointer.base.type.isArray()&&!wholeObjectPointer(pointer)?pointer.base.members.size():1;
        if(offset<0||offset>length)throw fail(range,"Constant pointer arithmetic leaves its object");return new PointerValue(pointer.base,(int)offset,pointer.type);
    }
    private boolean similarObjectType(CrakenType from,CrakenType to){
        from=from.unqualified();to=to.unqualified();
        if(from.isArray()&&to.isArray())return from.arrayLength()==to.arrayLength()&&similarObjectType(from.elementType(),to.elementType());
        if(from.isPointer()&&to.isPointer())return similarObjectType(from.pointee(),to.pointee());
        return from.equals(to);
    }
    private PointerValue indexBase(Value value,CrakenType pointerType,SourceRange range){
        if(value instanceof ObjectValue object&&object.type().isArray())return new PointerValue(object.storage,0,pointerType);
        if(value instanceof PointerValue pointer)return pointer;
        throw fail(range,"Indexing requires an array or object pointer in a constant expression");
    }
    private boolean wholeObjectPointer(PointerValue pointer){return pointer.base!=null&&pointer.type.isPointer()&&similarObjectType(pointer.type.pointee(),pointer.base.type);}
    private Value convert(Value value,CrakenType target,SourceRange range){
        if(target.isVoid())return new VoidValue();target=target.unqualified();
        if(target.equals(CrakenType.BOOL))return bool(truth(value,range));
        if(target.isPointer()||target.isNullPointer()){
            if(value instanceof PointerValue pointer)return new PointerValue(pointer.base,pointer.offset,target);
            if(value instanceof FunctionValue function)return new FunctionValue(function.name,target);
            if(value instanceof ObjectValue object&&object.type().isArray())return new PointerValue(object.storage,0,target);
            if(value instanceof IntegerValue integer&&integer.value==0)return new PointerValue(null,0,target);
            throw fail(range,"This pointer conversion is not a constant expression");
        }
        if(target.isIntegerScalar()){
            TemplateArgument.Integral integer;
            if(value instanceof FloatingValue number){
                if(!Double.isFinite(number.value))throw fail(range,"Floating to integral constant conversion is outside range");
                java.math.BigInteger truncated=new java.math.BigDecimal(number.value).toBigInteger();
                int width=((CrakenType.ScalarType)target).kind().sizeBytes()*8;
                java.math.BigInteger minimum=target.isSignedIntegerScalar()?java.math.BigInteger.ONE.shiftLeft(width-1).negate():java.math.BigInteger.ZERO;
                java.math.BigInteger maximum=java.math.BigInteger.ONE.shiftLeft(target.isSignedIntegerScalar()?width-1:width).subtract(java.math.BigInteger.ONE);
                if(truncated.compareTo(minimum)<0||truncated.compareTo(maximum)>0)throw fail(range,"Floating to integral constant conversion is outside range");
                return new IntegerValue(truncated.longValue(),target);
            }
            else if(value instanceof IntegerValue number)integer=new TemplateArgument.Integral(number.value,number.type);
            else throw fail(range,"Pointer to integral conversion is not a constant expression");
            var converted=TemplateValues.convert(integer,target,value instanceof FloatingValue);return new IntegerValue(converted.value(),converted.type());
        }
        if(target.isFloatingScalar()){
            // Match the binary32/binary64 runtime conversion profile (round to nearest,
            // including signed infinity). This is a floating conversion, not list
            // narrowing or an overflowing arithmetic operation; those are checked separately.
            double number=number(value,range);
            if(target.equals(CrakenType.FLOAT))number=(float)number;
            return new FloatingValue(number,target);
        }
        if(value instanceof ObjectValue object&&object.type().unqualified().equals(target))return value;
        throw fail(range,"Unsupported constant conversion to "+target);
    }
    private void initializeAt(Cell destination,Expression initializer,Frame frame){
        tick(initializer.range());
        if(initializer instanceof GroupingExpr group){initializeAt(destination,group.expression(),frame);return;}
        if(initializer instanceof ConditionalExpr choice){initializeAt(destination,truth(eval(choice.condition(),frame),choice.range())?choice.thenExpression():choice.elseExpression(),frame);return;}
        if(initializer instanceof CommaExpr comma){for(int i=0;i<comma.expressions().size()-1;i++)eval(comma.expressions().get(i),frame);initializeAt(destination,comma.expressions().getLast(),frame);return;}
        if(initializer instanceof CleanupExpr cleanup){initializeAt(destination,cleanup.value(),frame);eval(cleanup.cleanup(),frame);return;}
        if(initializer instanceof LetExpr capture){
            Frame nested=new Frame(frame,frame.returnStorage,true);Cell value=allocate(capture.type(),null,null,-1,capture.range());
            store(value,convert(eval(capture.initializer(),frame),capture.type(),capture.range()),true,capture.range());value.mutable=false;nested.names.put(capture.name(),value);
            initializeAt(destination,capture.body(),nested);frame.temporaries.addAll(nested.temporaries);return;
        }
        if(initializer instanceof ObjectInitExpr object){
            Frame nested=new Frame(frame,frame.returnStorage,true);Cell address=allocate(object.type().pointerTo(),null,null,-1,object.range());
            address.value=pointer(destination,object.type().pointerTo());address.initialized=true;address.mutable=false;nested.names.put(object.destinationName(),address);eval(object.body(),nested);frame.temporaries.addAll(nested.temporaries);destination.initialized=true;return;
        }
        if(initializer instanceof AggregateInitExpr aggregate){
            List<Cell> members=destination.type.isStruct()||destination.type.isArray()?new ArrayList<>(destination.members.values()):List.of(destination);
            if(aggregate.values().size()>members.size())throw fail(aggregate.range(),"Too many constant aggregate initializer elements");
            for(int i=0;i<members.size();i++){if(i<aggregate.values().size())initializeAt(members.get(i),aggregate.values().get(i),frame);else zero(members.get(i),aggregate.range());}destination.initialized=true;return;
        }
        if(initializer instanceof CallExpr call&&destination.type.isStruct()){invoke(call,frame,destination);return;}
        store(destination,convert(eval(initializer,frame),destination.type,initializer.range()),true,initializer.range());
    }
    private void zero(Cell cell,SourceRange range){if(cell.type.isStruct()||cell.type.isArray())for(Cell member:cell.members.values())zero(member,range);else store(cell,convert(new IntegerValue(0,CrakenType.INT),cell.type,range),true,range);cell.initialized=true;}
    private Value invoke(CallExpr call,Frame caller,Cell destination){
        Value callee=eval(call.callee(),caller);if(!(callee instanceof FunctionValue function))throw fail(call.range(),"Constant call does not name a known function");
        Function supplied=context.function(function.name);if(supplied==null||!supplied.constantEligible)throw fail(call.range(),"Call to a non-constexpr function: "+function.name);
        FunctionDecl declaration=supplied.declaration;if(!declaration.hasBody())throw fail(call.range(),"Constexpr function definition is not available");
        if(++calls>limits.calls)throw fail(call.range(),"Constant evaluation call depth limit exceeded");
        if(call.arguments().size()<declaration.parameters().size()||!declaration.variadic()&&call.arguments().size()!=declaration.parameters().size())throw fail(call.range(),"Incomplete constant call");
        if(destination==null&&declaration.returnType().isStruct())destination=allocate(declaration.returnType(),null,null,-1,call.range());
        Frame frame=new Frame(null,destination);
        try{
            List<Cell> parameters=new ArrayList<>();for(Parameter parameter:declaration.parameters()){Cell cell=allocate(parameter.type(),null,null,-1,parameter.range());frame.names.put(parameter.name(),cell);frame.owned.add(cell);parameters.add(cell);}
            for(int index:call.argumentEvaluationOrder()){if(index<parameters.size())initializeAt(parameters.get(index),call.arguments().get(index),caller);else eval(call.arguments().get(index),caller);}
            Completion result=execute(declaration.body(),frame);
            if(result.flow!=Flow.RETURN&&!declaration.returnType().isVoid())throw fail(call.range(),"Constexpr function reached its end without a return");
            return destination!=null?new ObjectValue(destination):convert(result.value,declaration.returnType(),call.range());
        }finally{close(frame);calls--;}
    }
    private Completion execute(Statement statement,Frame frame){
        if(statement==null)return Completion.normal();tick(statement.range());
        return switch(statement){
            case DeclGroupStmt group -> {Completion flow=Completion.normal();for(Statement child:group.statements()){flow=execute(child,frame);if(flow.flow!=Flow.NORMAL)break;}yield flow;}
            case BlockStmt block -> {Frame nested=new Frame(frame,frame.returnStorage);try{Completion flow=Completion.normal();for(Statement child:block.statements()){flow=execute(child,nested);if(flow.flow!=Flow.NORMAL)break;}yield flow;}finally{close(nested);}}
            case VarDeclStmt variable -> {
                if(variable.staticStorage())throw fail(variable.range(),"Static locals are not permitted in constexpr functions");
                Cell cell=allocate(variable.type(),null,null,-1,variable.range());frame.names.put(variable.name(),cell);frame.owned.add(cell);
                if(variable.initializer()==null)throw fail(variable.range(),"A constexpr local object must be initialized");try{initializeAt(cell,variable.initializer(),frame);}finally{endExpression(frame);}yield Completion.normal();
            }
            case ExprStmt expression -> {fullExpression(expression.expression(),frame);yield Completion.normal();}
            case ReturnStmt returned -> {
                if(frame.returnStorage!=null){initializeAt(frame.returnStorage,returned.expression(),frame);yield new Completion(Flow.RETURN,new ObjectValue(frame.returnStorage));}
                yield new Completion(Flow.RETURN,eval(returned.expression(),frame));
            }
            case IfStmt selection -> execute(truth(fullExpression(selection.condition(),frame),selection.range())?selection.thenBranch():selection.elseBranch(),frame);
            case ForStmt loop -> {
                Frame nested=new Frame(frame,frame.returnStorage);
                try{Completion flow=execute(loop.initializer(),nested);while(flow.flow==Flow.NORMAL&&(loop.condition()==null||truth(fullExpression(loop.condition(),nested),loop.range()))){flow=execute(loop.body(),nested);if(flow.flow==Flow.RETURN)break;if(flow.flow==Flow.BREAK){flow=Completion.normal();break;}flow=Completion.normal();fullExpression(loop.step(),nested);}yield flow;}
                finally{close(nested);}
            }
            case WhileStmt loop -> {Completion flow=Completion.normal();while(truth(fullExpression(loop.condition(),frame),loop.range())){flow=execute(loop.body(),frame);if(flow.flow==Flow.RETURN)break;if(flow.flow==Flow.BREAK){flow=Completion.normal();break;}flow=Completion.normal();}yield flow;}
            case DoWhileStmt loop -> {Completion flow;do{flow=execute(loop.body(),frame);if(flow.flow==Flow.RETURN)break;if(flow.flow==Flow.BREAK){flow=Completion.normal();break;}flow=Completion.normal();}while(truth(fullExpression(loop.condition(),frame),loop.range()));yield flow;}
            case SwitchStmt selection -> {
                long selector=integer(fullExpression(selection.selector(),frame),selection.range());int selected=-1,fallback=-1;
                for(int i=0;i<selection.cases().size();i++){var arm=selection.cases().get(i);if(arm.value()==null)fallback=i;else if(integer(eval(arm.value(),frame),arm.range())==selector)selected=i;}
                if(selected<0)selected=fallback;Frame nested=new Frame(frame,frame.returnStorage);Completion flow=Completion.normal();
                try{outer:for(int i=selected;i>=0&&i<selection.cases().size();i++)for(Statement child:selection.cases().get(i).statements()){flow=execute(child,nested);if(flow.flow!=Flow.NORMAL)break outer;}yield flow.flow==Flow.BREAK?Completion.normal():flow;}finally{close(nested);}
            }
            case BreakStmt ignored -> new Completion(Flow.BREAK,new VoidValue());
            case ContinueStmt ignored -> new Completion(Flow.CONTINUE,new VoidValue());
            case TypedefStmt ignored -> Completion.normal();
            case CleanupScopeStmt scope -> {Completion flow=execute(scope.body(),frame);eval(scope.cleanup(),frame);yield flow;}
            default -> throw fail(statement.range(),"This statement is not supported during constant evaluation");
        };
    }
    private void validateResult(Value value,SourceRange range,Set<Cell> seen){
        if(value instanceof PointerValue pointer&&pointer.base!=null){accessible(pointer.base,range);if(pointer.base.globalName==null)throw fail(range,"Constant pointer result refers to automatic storage");}
        if(value instanceof ObjectValue object&&seen.add(object.storage)){accessible(object.storage,range);for(Cell cell:object.storage.members.values())validateResult(read(cell,range),range,seen);}
    }
}
