package minic.compiler.ir.optimize;
import minic.compiler.ir.IrResult;
import minic.compiler.ir.instruction.IrInstruction;
import minic.compiler.ir.instruction.ComputeInstruction.*;
import minic.compiler.ir.model.*;
import minic.compiler.ir.value.IrValue.IrTemporary;
import java.util.*;
/** Writes a pure operation directly to its immediately following single-use copy's destination. */
public final class AdjacentResultForwardingPass implements IrPass {
    public String name(){return "adjacent-result-forwarding";}
    public IrResult apply(IrResult source){
        Objects.requireNonNull(source,"source");
        var functions=source.functions().stream().map(this::forward).toList();
        return functions.equals(source.functions())?source:new IrResult(functions,source.stringData(),source.globalData(),
                source.externalFunctionNames(),source.externalObjectNames(),source.structLayouts(),source.currentAstNode(),
                source.currentSubject(),source.displayNames(),source.entryFunction());
    }
    private IrFunction forward(IrFunction function){
        var definitions=new HashMap<String,Integer>();var uses=new HashMap<String,Integer>();
        for(var block:function.blocks())for(var instruction:block.instructions()){
            var result=IrValueUses.result(instruction);
            if(result!=null)definitions.merge(result.name(),1,Integer::sum);
            for(var value:IrValueUses.inputs(instruction))if(value instanceof IrTemporary temporary)uses.merge(temporary.name(),1,Integer::sum);
        }
        var flow=IrControlFlow.analyze(function);var blocks=new ArrayList<IrBlock>();
        for(var block:function.blocks()){
            if(!flow.reachable().contains(block.label())){blocks.add(block);continue;}
            var prefix=flow.effectiveInstructions(block.label());var code=new ArrayList<IrInstruction>();
            for(var instruction:prefix){
                if(instruction instanceof IrMoveInstruction move && move.value() instanceof IrTemporary temporary && !code.isEmpty()
                        && move.result().type()==temporary.type() && definitions.getOrDefault(temporary.name(),0)==1
                        && uses.getOrDefault(temporary.name(),0)==1){
                    var producer=code.getLast();
                    if(temporary.equals(IrValueUses.result(producer))){
                        IrInstruction replacement=switch(producer){
                            case IrBinaryInstruction value -> new IrBinaryInstruction(move.result(),value.operator(),value.left(),value.right(),value.range());
                            case IrUnaryInstruction value -> new IrUnaryInstruction(move.result(),value.operator(),value.operand(),value.range());
                            case IrCastInstruction value -> new IrCastInstruction(move.result(),value.value(),value.range());
                            default -> null;
                        };
                        if(replacement!=null){code.set(code.size()-1,replacement);continue;}
                    }
                }
                code.add(instruction);
            }
            code.addAll(block.instructions().subList(prefix.size(),block.instructions().size()));
            blocks.add(code.equals(block.instructions())?block:new IrBlock(block.label(),code));
        }
        return blocks.equals(function.blocks())?function:new IrFunction(function.name(),function.returnType(),function.parameters(),function.variadic(),blocks,function.range());
    }
}
