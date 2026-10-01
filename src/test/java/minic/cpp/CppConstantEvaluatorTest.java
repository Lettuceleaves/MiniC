package minic.cpp;

import minic.SourceRange;
import minic.compiler.lexer.token.TokenType;
import minic.compiler.parser.node.*;
import minic.compiler.parser.node.Expression.*;
import minic.compiler.parser.node.Declaration.*;
import minic.compiler.parser.node.Statement.*;
import minic.compiler.semantic.cpp.CppConstantEvaluator;
import minic.compiler.semantic.cpp.CppConstantEvaluator.*;
import minic.compiler.type.MiniType;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Saved for the final unified run; these tests exercise the evaluator without native execution. */
final class CppConstantEvaluatorTest {
    static final SourceRange R=new SourceRange(1,0,1,10);
    static final MiniType BOX=MiniType.struct("Box");
    static IntegerLiteralExpr n(int value){return new IntegerLiteralExpr(value,Integer.toString(value),R);}
    static NameExpr name(String value){return new NameExpr(value,R);}
    static BinaryExpr op(Expression a,TokenType op,Expression b){return new BinaryExpr(a,op,b,R);}
    static UnaryExpr address(Expression value){return new UnaryExpr(TokenType.AMPERSAND,value,R);}
    static UnaryExpr dereference(Expression value){return new UnaryExpr(TokenType.STAR,value,R);}
    static ExprStmt assignment(Expression target,Expression value){return new ExprStmt(new AssignmentExpr(target,TokenType.EQUAL,value,R),R);}
    static FunctionDecl function(String name,MiniType result,List<Parameter> parameters,Statement... statements){return new FunctionDecl(name,result,parameters,false,new BlockStmt(List.of(statements),R),false,false,R);}
    static CallExpr call(String name,Expression... arguments){return new CallExpr(name(name),List.of(arguments),R);}
    static long integer(Value value){return ((IntegerValue)value).value();}
    static final class Env implements Context {
        final Map<String,Function> functions=new HashMap<>();
        final Map<String,Global> globals=new HashMap<>();
        final Map<String,MiniType> names=new HashMap<>();
        final IdentityHashMap<Expression,MiniType> types=new IdentityHashMap<>();
        public Function function(String name){return functions.get(name);}
        public Global global(String name){return globals.get(name);}
        void function(FunctionDecl value,boolean eligible){functions.put(value.name(),new Function(value,eligible));}
        public MiniType type(Expression expression){
            MiniType supplied=types.get(expression);if(supplied!=null)return supplied;
            return switch(expression){
                case NameExpr name when functions.containsKey(name.name()) -> MiniType.function(functions.get(name.name()).declaration().returnType(),functions.get(name.name()).declaration().parameters().stream().map(Parameter::type).toList(),false);
                case NameExpr name -> names.getOrDefault(name.name(),MiniType.INT);
                case CastExpr cast -> cast.targetType();
                case StringLiteralExpr literal -> MiniType.CHAR.arrayOf(literal.value().length()+1);
                case UnaryExpr unary when unary.operator()==TokenType.AMPERSAND -> type(unary.operand()).pointerTo();
                case UnaryExpr unary when unary.operator()==TokenType.STAR -> type(unary.operand()).pointee();
                case IndexExpr index -> type(index.target()).isArray()?type(index.target()).elementType():type(index.target()).pointee();
                case MaterializeExpr materialize -> materialize.type().pointerTo();
                case ObjectInitExpr object -> object.type();
                case CallExpr call -> type(call.callee()).returnType();
                case FieldAccessExpr field -> field.fieldName().equals("self")?BOX.pointerTo():MiniType.INT;
                default -> MiniType.INT;
            };
        }
        public List<StructField> fields(MiniType type){return type.unqualified().equals(BOX)?List.of(new StructField("value",MiniType.INT,R),new StructField("self",BOX.pointerTo(),R)):List.of();}
        public int sizeOf(MiniType type){return type.isPointer()?8:type.isStruct()?16:type.isArray()?type.arrayLength()*sizeOf(type.elementType()):4;}
        public int alignmentOf(MiniType type){return type.isPointer()||type.isStruct()?8:4;}
        CppConstantEvaluator evaluator(){return new CppConstantEvaluator(this,Limits.defaults());}
    }
    @Test void eligibleLoopFunctionUsesMutableLocalsAndReferenceArguments(){
        Env env=new Env();env.names.put("p",MiniType.INT.pointerTo());
        env.function(function("sum",MiniType.INT,List.of(new Parameter("p",MiniType.INT.pointerTo(),R)),
                new VarDeclStmt("sum",MiniType.INT,n(0),R),
                new ForStmt(new VarDeclStmt("i",MiniType.INT,n(0),R),op(name("i"),TokenType.LESS,n(5)),new PostfixUpdateExpr(name("i"),TokenType.PLUS_PLUS,R),
                        assignment(name("sum"),op(name("sum"),TokenType.PLUS,name("i"))),R),
                assignment(dereference(name("p")),name("sum")),new ReturnStmt(name("sum"),R)),true);
        env.function(function("outer",MiniType.INT,List.of(),new VarDeclStmt("x",MiniType.INT,n(0),R),new ExprStmt(call("sum",address(name("x"))),R),new ReturnStmt(name("x"),R)),true);
        assertEquals(10,integer(env.evaluator().evaluate(call("outer"))));
    }
    @Test void anOrdinaryPureFunctionRemainsIneligible(){Env env=new Env();env.function(function("ordinary",MiniType.INT,List.of(),new ReturnStmt(n(7),R)),false);assertThrows(Failure.class,()->env.evaluator().evaluate(call("ordinary")));}
    @Test void stepAndCallBudgetsRejectNonterminatingEvaluation(){
        Env env=new Env();env.function(function("forever",MiniType.INT,List.of(),new WhileStmt(n(1),new ExprStmt(n(0),R),R)),true);
        assertThrows(Failure.class,()->new CppConstantEvaluator(env,new Limits(30,10,30)).evaluate(call("forever")));
        env.function(function("recurse",MiniType.INT,List.of(),new ReturnStmt(call("recurse"),R)),true);
        assertThrows(Failure.class,()->new CppConstantEvaluator(env,new Limits(1000,4,100)).evaluate(call("recurse")));
    }
    @Test void globalAddressesHaveIdentityWithoutReadingTheirValues(){
        Env env=new Env();env.globals.put("external",new Global(MiniType.INT,null,false));
        assertEquals(1,integer(env.evaluator().evaluate(op(address(name("external")),TokenType.EQUAL_EQUAL,address(name("external"))))));
        assertThrows(Failure.class,()->env.evaluator().evaluate(name("external")));
    }
    @Test void globalReadsAndWritesRespectConstantEligibility(){
        Env env=new Env();env.globals.put("constant",new Global(MiniType.INT,n(3),true));
        assertEquals(3,integer(env.evaluator().evaluate(name("constant"))));
        assertThrows(Failure.class,()->env.evaluator().evaluate(new AssignmentExpr(name("constant"),TokenType.EQUAL,n(4),R)));
        env.globals.put("volatile",new Global(MiniType.qualified(MiniType.INT,Set.of(MiniType.TypeQualifier.VOLATILE)),n(1),true));
        assertThrows(Failure.class,()->env.evaluator().evaluate(name("volatile")));
    }
    @Test void arrayPointersCanAdvanceToOnePastButCannotReadThere(){
        Env env=new Env();env.names.put("a",MiniType.INT.arrayOf(2));env.globals.put("a",new Global(MiniType.INT.arrayOf(2),new AggregateInitExpr(List.of(n(2),n(3)),R),true));
        Expression begin=address(new IndexExpr(name("a"),n(0),R));Expression end=op(begin,TokenType.PLUS,n(2));
        assertEquals(2,integer(env.evaluator().evaluate(op(end,TokenType.MINUS,begin))));
        assertThrows(Failure.class,()->env.evaluator().evaluate(dereference(end)));
    }
    @Test void constructionUsesTheFinalSymbolicObjectAddress(){
        Env env=new Env();env.names.put("destination",BOX.pointerTo());
        Expression initialize=new ObjectInitExpr(BOX,"destination",new CommaExpr(List.of(
                new InitializeExpr(new FieldAccessExpr(name("destination"),"value",true,R),n(7),R),
                new InitializeExpr(new FieldAccessExpr(name("destination"),"self",true,R),name("destination"),R)),R),R);
        CppConstantEvaluator evaluator=env.evaluator();evaluator.initialize("box",BOX,initialize);env.names.put("box",BOX);
        assertEquals(1,integer(evaluator.evaluate(op(new FieldAccessExpr(name("box"),"self",false,R),TokenType.EQUAL_EQUAL,address(name("box"))))));
    }
    @Test void pointerResultsCannotEscapeAutomaticObjectLifetime(){
        Env env=new Env();env.function(function("dangling",MiniType.INT.pointerTo(),List.of(),new VarDeclStmt("x",MiniType.INT,n(1),R),new ReturnStmt(address(name("x")),R)),true);
        assertThrows(Failure.class,()->env.evaluator().evaluate(call("dangling")));
    }
    @Test void fullExpressionTemporaryExpiresBeforeTheFollowingStatement(){
        Env env=new Env();env.names.put("p",MiniType.INT.pointerTo());
        Expression temporary=new MaterializeExpr(MiniType.INT,n(3),new TemporaryLifetime(TemporaryLifetime.Kind.FULL_EXPRESSION,n(3)),R);
        env.function(function("dangling",MiniType.INT,List.of(),new VarDeclStmt("p",MiniType.INT.pointerTo(),temporary,R),new ReturnStmt(dereference(name("p")),R)),true);
        assertThrows(Failure.class,()->env.evaluator().evaluate(call("dangling")));
    }
    @Test void referenceScopeTemporarySurvivesUntilItsBlockEnds(){
        Env env=new Env();env.names.put("p",MiniType.INT.pointerTo());
        Expression temporary=new MaterializeExpr(MiniType.INT,n(3),new TemporaryLifetime(TemporaryLifetime.Kind.REFERENCE_SCOPE,n(3)),R);
        env.function(function("safe",MiniType.INT,List.of(),new VarDeclStmt("p",MiniType.INT.pointerTo(),temporary,R),new ReturnStmt(dereference(name("p")),R)),true);
        assertEquals(3,integer(env.evaluator().evaluate(call("safe"))));
    }
    @Test void shortCircuitAndSizeofDoNotEvaluateInvalidOperands(){
        Env env=new Env();Expression invalid=op(n(1),TokenType.SLASH,n(0));
        assertEquals(0,integer(env.evaluator().evaluate(op(n(0),TokenType.AMPERSAND_AMPERSAND,invalid))));
        assertEquals(4,integer(env.evaluator().evaluate(new SizeofExpr(invalid,null,R))));
    }
    @Test void emptyRecordsAndStringLiteralsAreConstantObjects(){
        Env env=new Env();assertInstanceOf(ObjectValue.class,env.evaluator().initialize("empty",MiniType.struct("Empty"),new AggregateInitExpr(List.of(),R)));Expression literal=new StringLiteralExpr("ab","\"ab\"",R);
        assertEquals(98,integer(env.evaluator().evaluate(new IndexExpr(literal,n(1),R))));
        assertEquals(1,integer(env.evaluator().evaluate(op(new DoubleLiteralExpr(2,"2.",R),TokenType.AMPERSAND_AMPERSAND,new DoubleLiteralExpr(3,"3.",R)))));
    }
}
