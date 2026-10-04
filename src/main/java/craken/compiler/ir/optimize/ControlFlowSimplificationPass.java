package craken.compiler.ir.optimize;

import craken.compiler.ir.IrResult;
import craken.compiler.ir.instruction.ControlInstruction.IrJumpInstruction;
import craken.compiler.ir.instruction.ControlInstruction.IrBranchInstruction;
import craken.compiler.ir.instruction.IrInstruction;
import craken.compiler.ir.model.IrBlock;
import craken.compiler.ir.model.IrFunction;
import java.util.*;

/** Joins unconditional straight-line control-flow chains after constant folding. */
public final class ControlFlowSimplificationPass implements IrPass {
    @Override public String name() { return "control-flow-simplification"; }
    @Override public IrResult apply(IrResult input) {
        Objects.requireNonNull(input);
        var functions=input.functions().stream().map(this::simplify).toList();
        return functions.equals(input.functions())?input:new IrResult(functions,input.stringData(),input.globalData(),
                input.externalFunctionNames(),input.externalObjectNames(),input.structLayouts(),input.currentAstNode(),
                input.currentSubject(),input.displayNames(),input.entryFunction());
    }
    private IrFunction simplify(IrFunction function) {
        if(function.blocks().isEmpty())return function;
        // Count every predecessor, including unreachable blocks: this pass does
        // not remove dead blocks and must never leave their edges dangling.
        var predecessors=new HashMap<String,Set<String>>();
        for(var block:function.blocks())for(var instruction:block.instructions()) {
            List<String> targets=switch(instruction) {
                case IrJumpInstruction jump -> List.of(jump.targetLabel());
                case IrBranchInstruction branch -> List.of(branch.thenLabel(),branch.elseLabel());
                default -> List.of();
            };
            for(String target:targets)predecessors.computeIfAbsent(target,key->new HashSet<>()).add(block.label());
        }
        var blocks=new LinkedHashMap<String,IrBlock>();
        function.blocks().forEach(block->blocks.put(block.label(),block));
        String entry=function.blocks().getFirst().label();
        boolean changed=false;
        for(String name:List.copyOf(blocks.keySet())) {
            IrBlock current=blocks.get(name);
            while(current!=null && !current.instructions().isEmpty()
                    && current.instructions().getLast() instanceof IrJumpInstruction jump) {
                String target=jump.targetLabel();
                IrBlock next=blocks.get(target);
                if(next==null || target.equals(name) || target.equals(entry)
                        || !predecessors.getOrDefault(target,Set.of()).equals(Set.of(name))
                        || hasEarlyTerminator(current) || hasEarlyTerminator(next))break;
                var body=new ArrayList<IrInstruction>(current.instructions().subList(0,current.instructions().size()-1));
                body.addAll(next.instructions());
                current=new IrBlock(name,body);blocks.put(name,current);blocks.remove(target);
                // The old target's outgoing edges now originate at its predecessor.
                for(var incoming:predecessors.values())if(incoming.remove(target))incoming.add(name);
                predecessors.remove(target);
                changed=true;
            }
        }
        return changed?new IrFunction(function.name(),function.returnType(),function.parameters(),function.variadic(),
                List.copyOf(blocks.values()),function.range()):function;
    }
    private static boolean hasEarlyTerminator(IrBlock block) {
        for(int i=0;i<block.instructions().size()-1;i++)if(IrControlFlow.isTerminator(block.instructions().get(i)))return true;
        return false;
    }
}
