package minic.compiler.ir.optimize;

import minic.compiler.ir.instruction.MemoryInstruction.*;
import minic.compiler.ir.model.IrFunction;
import minic.compiler.ir.value.IrValue.IrTemporary;
import java.util.*;

/** Adjacent native addressing groups; source/debug IR and complete temporary liveness are unchanged. */
public final class FieldLoadPlan {
    public record Fusion(IrFieldAddressInstruction fieldAddress, IrLoadPointerInstruction load) {
        public int instructionCount() { return 2; }
    }
    private final Map<String,Map<Integer,Fusion>> groups;
    private FieldLoadPlan(IrFunction function) {
        Map<String,Integer> definitions=new HashMap<>(),uses=new HashMap<>();
        // Include unreachable blocks/dead tails: a second static definition/use prevents discarding the home.
        for(var block:function.blocks())for(var instruction:block.instructions()) {
            var result=IrValueUses.result(instruction);
            if(result!=null)definitions.merge(result.name(),1,Integer::sum);
            for(var input:IrValueUses.inputs(instruction))if(input instanceof IrTemporary temporary)
                uses.merge(temporary.name(),1,Integer::sum);
        }
        var flow=IrControlFlow.analyze(function);var planned=new HashMap<String,Map<Integer,Fusion>>();
        for(var block:function.blocks())if(flow.reachable().contains(block.label())) {
            var code=flow.effectiveInstructions(block.label());var matches=new HashMap<Integer,Fusion>();
            for(int i=0;i+1<code.size();i++) {
                if(code.get(i) instanceof IrFieldAddressInstruction field
                        && code.get(i+1) instanceof IrLoadPointerInstruction load
                        && !load.volatileAccess() && load.address().equals(field.result())
                        && definitions.getOrDefault(field.result().name(),0)==1
                        && uses.getOrDefault(field.result().name(),0)==1) {
                    // Field IR validates a nonnegative int offset, exactly the positive disp32 range.
                    matches.put(i,new Fusion(field,load));i++;
                }
            }
            if(!matches.isEmpty())planned.put(block.label(),Map.copyOf(matches));
        }
        groups=Map.copyOf(planned);
    }
    public static FieldLoadPlan analyze(IrFunction function){return new FieldLoadPlan(Objects.requireNonNull(function));}
    public Fusion at(String block,int index){return groups.getOrDefault(block,Map.of()).get(index);}
}
