package minic.compiler.ir.optimize;

import minic.compiler.ir.IrResult;
import minic.compiler.ir.instruction.IrInstruction;
import minic.compiler.ir.instruction.ComputeInstruction.IrMoveInstruction;
import minic.compiler.ir.model.IrBlock;
import minic.compiler.ir.model.IrFunction;
import minic.compiler.ir.value.IrValue;
import minic.compiler.ir.value.IrValue.IrTemporary;
import java.util.*;

/** Propagates temporary copies only while their same-block source value is unchanged. */
public final class BlockCopyPropagationPass implements IrPass {
    @Override public String name() { return "block-copy-propagation"; }
    @Override public IrResult apply(IrResult source) {
        Objects.requireNonNull(source,"source");
        var functions=source.functions().stream().map(this::rewrite).toList();
        return functions.equals(source.functions())?source:new IrResult(functions,source.stringData(),source.globalData(),
                source.externalFunctionNames(),source.externalObjectNames(),source.structLayouts(),source.currentAstNode(),
                source.currentSubject(),source.displayNames(),source.entryFunction());
    }
    private IrFunction rewrite(IrFunction function) {
        var flow=IrControlFlow.analyze(function);
        var blocks=new ArrayList<IrBlock>();
        for(var block:function.blocks()) {
            if(!flow.reachable().contains(block.label())) {blocks.add(block);continue;}
            var copies=new HashMap<String,IrTemporary>();
            var code=new ArrayList<IrInstruction>();
            for(var instruction:flow.effectiveInstructions(block.label())) {
                // A promoted home may have many definitions. Each operand observes its
                // value before this instruction writes its result; no facts cross an edge.
                var rewritten=IrValueRewriter.inputs(instruction,value->resolve(value,copies));
                var result=IrValueUses.result(instruction);
                if(result!=null) {
                    kill(result.name(),copies);
                    if(rewritten instanceof IrMoveInstruction move && move.value() instanceof IrTemporary value
                            && value.type()==result.type() && !value.name().equals(result.name()))
                        copies.put(result.name(),value);
                }
                code.add(rewritten.equals(instruction)?instruction:rewritten);
            }
            code.addAll(block.instructions().subList(code.size(),block.instructions().size()));
            blocks.add(code.equals(block.instructions())?block:new IrBlock(block.label(),code));
        }
        return blocks.equals(function.blocks())?function:new IrFunction(function.name(),function.returnType(),
                function.parameters(),function.variadic(),blocks,function.range());
    }
    private static IrValue resolve(IrValue value,Map<String,IrTemporary> copies) {
        var visited=new HashSet<String>();
        while(value instanceof IrTemporary temporary && visited.add(temporary.name())) {
            var source=copies.get(temporary.name());
            if(source==null || source.type()!=value.type())break;
            value=source;
        }
        return value;
    }
    private static void kill(String name,Map<String,IrTemporary> copies) {
        var killed=new HashSet<String>();killed.add(name);
        boolean changed;
        do {
            changed=false;
            for(var copy:copies.entrySet())if(killed.contains(copy.getValue().name()))changed|=killed.add(copy.getKey());
        } while(changed);
        killed.forEach(copies::remove);
    }
}
