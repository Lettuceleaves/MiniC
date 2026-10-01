package minic.cpp;

import minic.SourceRange;
import minic.compiler.*;
import minic.compiler.asm.Assembler;
import minic.compiler.ir.IrResult;
import minic.compiler.ir.instruction.IrInstruction;
import minic.compiler.ir.instruction.CallInstruction.*;
import minic.compiler.ir.instruction.ComputeInstruction.*;
import minic.compiler.ir.instruction.ControlInstruction.*;
import minic.compiler.ir.instruction.MemoryInstruction.*;
import minic.compiler.ir.model.*;
import minic.compiler.ir.optimize.*;
import minic.compiler.ir.value.IrValue;
import minic.compiler.ir.value.IrValue.*;
import minic.compiler.link.Linker;
import minic.compiler.obj.ObjBuilder;
import minic.compiler.semantic.model.StructLayout;
import minic.compiler.semantic.model.StructLayout.StructFieldLayout;
import minic.compiler.type.MiniType;
import minic.cpp.support.BoundedProcess;
import minic.cpp.support.CppDifferentialHarness;
import minic.debug.DebugApi;
import minic.debug.Debugger;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Hand IR keeps all twelve result instructions observable; an empty pass list isolates native placement. */
@Timeout(90)
final class CppRegisterUseDefPositionTest {
    private static final SourceRange R=new SourceRange(1,0,1,1);
    private enum Kind { BINARY,UNARY,CAST,MOVE,SELECT,ADDRESS_LOCAL,LOAD_LOCAL,ELEMENT_ADDRESS,FIELD_ADDRESS,LOAD_POINTER,CALL,INDIRECT_CALL }
    @TempDir Path temporary;
    @ParameterizedTest @EnumSource(value=IrType.class,names={"SIGNED_CHAR","UNSIGNED_CHAR","SHORT","UNSIGNED_SHORT","INT","UNSIGNED_INT","LONG_LONG","UNSIGNED_LONG_LONG","FLOAT","DOUBLE"})
    void everyResultFormPreservesItsInputsAndTypedResult(IrType type)throws Exception {
        var declared=declared(type);var functions=new ArrayList<IrFunction>();
        for(var kind:Kind.values())functions.add(operation(kind,type,declared));
        var input=new IrParameter("input",declared,type,R);var copied=new IrTemporary("copied",type);
        functions.add(new IrFunction("identity",declared,List.of(input),false,List.of(block("entry",new IrMoveInstruction(copied,input.ref(),R),new IrReturnInstruction(copied,R))),R));
        var main=new ArrayList<IrBlock>();int n=0;
        long other=type.isSignedInteger()?-7:type.isFloatingScalar()?-7:type.sizeBytes()==1?249:type.sizeBytes()==2?65529:type.sizeBytes()==4?4294967289L:4294967303L;
        StringBuilder reference=new StringBuilder("typedef "+spelling(type)+" Value; struct Record{char pad[128];Value field;}; Value identity(Value x){return x;}");
        for(var kind:Kind.values())reference.append("Value ").append(kind.name()).append("(Value input){").append(body(kind)).append('}');
        reference.append("int main(){");
        for(long value:new long[]{7,other})for(var kind:Kind.values()) {
            var result=new IrTemporary("result"+n,type);var wrong=new IrTemporary("wrong"+n,IrType.INT);
            main.add(block(n==0?"entry":"case"+n,new IrCallInstruction(result,kind.name(),List.of(constant(value,type)),false,R),
                    new IrBinaryInstruction(wrong,IrBinaryOperator.NOT_EQUAL,result,constant(value,type),R),new IrBranchInstruction(wrong,"failure","case"+(n+1),R)));
            reference.append("if(").append(kind.name()).append("((Value)").append(value).append("LL)!=(Value)").append(value).append("LL)return 1;");n++;
        }
        main.add(block("case"+n,new IrReturnInstruction(new IrConstant(0),R)));
        main.add(block("failure",new IrReturnInstruction(new IrConstant(1),R)));
        functions.addFirst(new IrFunction("main",MiniType.INT,List.of(),false,main,R));reference.append("return 0;}");
        var layout=new StructLayout("Record",128+type.sizeBytes(),type.sizeBytes(),List.of(new StructFieldLayout("field",declared,128,type.sizeBytes(),type.sizeBytes())));
        var ir=new IrResult(functions,List.of(),Set.of(),Map.of("Record",layout));IrVerifier.verify(ir);
        var source=new SourceFile(type+".cpp",reference.toString());
        for(var level:OptimizationLevel.values()) {
            var assembler=new Assembler(ir,new IrOptimizationPipeline(level,List.of()));Path dir=temporary.resolve(level.name());
            var object=new ObjBuilder(source,assembler,dir,"program");var linker=new Linker(source,object,dir,"program");
            new CompilerApi(List.of(assembler,object,linker)).runThrough(linker);
            assertTrue(linker.succeeded(),()->assembler.errors()+" / "+object.errors()+" / "+linker.errors());
            assertEquals(0,run(dir.resolve("program.exe")).exitCode(),type+" "+level);
            assertSame(ir,assembler.input().irResult());
            if(level==OptimizationLevel.OPTIMIZED&&!type.isFloatingScalar()) {
                for(var kind:List.of(Kind.BINARY,Kind.UNARY,Kind.MOVE,Kind.CALL,Kind.INDIRECT_CALL)) {
                    var plan=GlobalRegisterPlan.allocate(functions.stream().filter(f->f.name().equals(kind.name())).findFirst().orElseThrow(),true);
                    assertEquals(plan.registers().get("input"),plan.registers().get("result"),kind+" must really reuse a dying input home");
                }
                for(var kind:List.of(Kind.LOAD_POINTER,Kind.ELEMENT_ADDRESS,Kind.FIELD_ADDRESS,Kind.LOAD_LOCAL)) {
                    var plan=GlobalRegisterPlan.allocate(functions.stream().filter(f->f.name().equals(kind.name())).findFirst().orElseThrow(),true);
                    String old=kind==Kind.LOAD_LOCAL?"input":kind==Kind.LOAD_POINTER?"base":"address";
                    assertEquals(plan.registers().get(old),plan.registers().get("result"),kind+" must reuse the consumed typed/pointer home");
                }
            }
        }
        var debug=DebugApi.fromIr(source,ir,"");for(int i=0;debug.canNext()&&i<20_000;i++)debug.next();
        assertEquals(Debugger.Status.COMPLETED,debug.current().stop().status(),debug.current().stop()::error);
        assertEquals(0,debug.current().runtime().termination().status());
        Path cpp=temporary.resolve("reference.cpp"),exe=temporary.resolve("reference.exe");Files.writeString(cpp,reference.toString());
        var compiler=BoundedProcess.run(List.of(CppDifferentialHarness.referenceCompiler(System.getenv()),"-std=c++17","-O2",cpp.toString(),"-o",exe.toString()),temporary,"",Duration.ofSeconds(20),65_536);
        assertFalse(compiler.timedOut());assertFalse(compiler.outputExceeded());assertEquals(0,compiler.exitCode(),compiler::stderr);
        assertEquals(0,run(exe).exitCode());
    }
    private BoundedProcess.Result run(Path exe)throws Exception {
        var result=BoundedProcess.run(List.of(exe.toString()),temporary,"",Duration.ofSeconds(5),65_536);
        assertFalse(result.timedOut());assertFalse(result.outputExceeded());return result;
    }
    private static IrFunction operation(Kind kind,IrType type,MiniType declared) {
        var parameter=new IrParameter("input",declared,type,R);var input=new IrTemporary("input",type);
        var middle=new IrTemporary("middle",type);var result=new IrTemporary("result",type);
        var code=new ArrayList<IrInstruction>();code.add(new IrMoveInstruction(input,parameter.ref(),R));
        switch(kind) {
            case BINARY -> {code.add(new IrBinaryInstruction(middle,IrBinaryOperator.SUBTRACT,input,constant(3,type),R));code.add(new IrBinaryInstruction(result,IrBinaryOperator.ADD,middle,constant(3,type),R));}
            case UNARY -> {code.add(new IrUnaryInstruction(middle,IrUnaryOperator.NEGATE,input,R));code.add(new IrUnaryInstruction(result,IrUnaryOperator.NEGATE,middle,R));}
            case CAST -> {var wide=new IrTemporary("wide",IrType.LONG_LONG);code.add(new IrCastInstruction(wide,input,R));code.add(new IrCastInstruction(result,wide,R));}
            case MOVE -> code.add(new IrMoveInstruction(result,input,R));
            case SELECT -> {var condition=new IrTemporary("condition",IrType.INT);var chosen=new IrTemporary("chosen",type);
                code.add(new IrMoveInstruction(condition,new IrConstant(0),R));code.add(new IrMoveInstruction(middle,constant(99,type),R));
                code.add(new IrSelectInstruction(chosen,condition,middle,input,R));code.add(new IrMoveInstruction(condition,new IrConstant(1),R));
                code.add(new IrSelectInstruction(result,condition,chosen,middle,R));}
            case CALL -> code.add(new IrCallInstruction(result,"identity",List.of(input),false,R));
            case INDIRECT_CALL -> {var callee=new IrTemporary("callee",IrType.POINTER);code.add(new IrMoveInstruction(callee,new IrFunctionAddress("identity"),R));code.add(new IrIndirectCallInstruction(result,callee,List.of(input),false,R));}
            default -> {
                code.clear();
                boolean field=kind==Kind.FIELD_ADDRESS;
                var local=new IrLocal("local","local",field?MiniType.struct("Record"):declared,field?IrType.POINTER:type,field?128+type.sizeBytes():type.sizeBytes(),type.sizeBytes(),R);
                var base=new IrTemporary("base",IrType.POINTER);var address=new IrTemporary("address",IrType.POINTER);
                code.add(new IrDeclareLocalInstruction(local,R));
                if(kind==Kind.LOAD_LOCAL) {code.add(new IrMoveInstruction(input,parameter.ref(),R));code.add(new IrStoreLocalInstruction(local,input,R));code.add(new IrLoadLocalInstruction(result,local,R));}
                else {
                    code.add(new IrAddressOfLocalInstruction(base,local,R));
                    IrTemporary pointer=base;
                    if(kind==Kind.ELEMENT_ADDRESS) {var index=new IrTemporary("index",IrType.INT);code.add(new IrMoveInstruction(index,new IrConstant(0),R));code.add(new IrElementAddressInstruction(address,base,index,declared,type.sizeBytes(),R));pointer=address;}
                    if(field) {code.add(new IrFieldAddressInstruction(address,base,"Record","field",128,declared,R));pointer=address;}
                    code.add(new IrMoveInstruction(input,parameter.ref(),R));code.add(new IrStorePointerInstruction(pointer,input,R));code.add(new IrLoadPointerInstruction(result,pointer,R));
                }
            }
        }
        code.add(new IrReturnInstruction(result,R));return new IrFunction(kind.name(),declared,List.of(parameter),false,List.of(new IrBlock("entry",code)),R);
    }
    private static String body(Kind kind){return switch(kind){
        case BINARY->"Value x=(Value)(input-3);return (Value)(x+3);";case UNARY->"Value x=(Value)-input;return (Value)-x;";
        case CAST->"long long x=(long long)input;return (Value)x;";case MOVE->"Value x=input;return x;";
        case SELECT->"int condition=0;Value middle=99;Value chosen=condition?middle:input;condition=1;return condition?chosen:middle;";case CALL->"return identity(input);";
        case INDIRECT_CALL->"Value(*p)(Value)=identity;return p(input);";case FIELD_ADDRESS->"Record r;r.field=input;return r.field;";
        case ELEMENT_ADDRESS->"Value x;Value*p=&x+0;*p=input;return *p;";case LOAD_LOCAL->"Value x=input;return x;";
        default->"Value x;Value*p=&x;*p=input;return *p;";};}
    private static IrValue constant(long value,IrType type){return type.isFloatingScalar()?new IrFloatConstant(value,type):new IrConstant(value,type);}
    private static IrBlock block(String name,IrInstruction... code){return new IrBlock(name,List.of(code));}
    private static String spelling(IrType type){return switch(type){case SIGNED_CHAR->"signed char";case UNSIGNED_CHAR->"unsigned char";case SHORT->"short";case UNSIGNED_SHORT->"unsigned short";case INT->"int";case UNSIGNED_INT->"unsigned int";case LONG_LONG->"long long";case UNSIGNED_LONG_LONG->"unsigned long long";case FLOAT->"float";case DOUBLE->"double";default->throw new IllegalArgumentException();};}
    private static MiniType declared(IrType type){return switch(type){case SIGNED_CHAR->MiniType.SIGNED_CHAR;case UNSIGNED_CHAR->MiniType.UNSIGNED_CHAR;case SHORT->MiniType.SHORT;case UNSIGNED_SHORT->MiniType.UNSIGNED_SHORT;case INT->MiniType.INT;case UNSIGNED_INT->MiniType.UNSIGNED_INT;case LONG_LONG->MiniType.LONG_LONG;case UNSIGNED_LONG_LONG->MiniType.UNSIGNED_LONG_LONG;case FLOAT->MiniType.FLOAT;case DOUBLE->MiniType.DOUBLE;default->throw new IllegalArgumentException();};}
}
