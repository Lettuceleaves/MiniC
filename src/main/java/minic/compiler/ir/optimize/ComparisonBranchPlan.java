package minic.compiler.ir.optimize;

import minic.compiler.ir.instruction.IrInstruction;
import minic.compiler.ir.instruction.ComputeInstruction.*;
import minic.compiler.ir.instruction.ControlInstruction.IrBranchInstruction;
import minic.compiler.ir.model.*;
import minic.compiler.ir.value.IrValue.IrTemporary;
import java.util.*;

/** Native emission groups only; leaves the source/debug IR and its ranges unchanged. */
public final class ComparisonBranchPlan {
    /** Branch targets are expressed in terms of the original comparison's truth value. */
    public record Fusion(IrBinaryInstruction comparison, IrBranchInstruction branch, int instructionCount,
                         List<IrTemporary> discardedTemporaries) {
        public Fusion { discardedTemporaries = List.copyOf(discardedTemporaries); }
        public Fusion(IrBinaryInstruction comparison, IrBranchInstruction branch, int instructionCount) {
            this(comparison, branch, instructionCount,
                    branch.condition() instanceof IrTemporary condition && !condition.equals(comparison.result())
                            ? List.of(comparison.result(), condition) : List.of(comparison.result()));
        }
    }
    private final Map<String,Map<Integer,Fusion>> groups;

    private ComparisonBranchPlan(IrFunction function) {
        Map<String,Integer> definitions=new HashMap<>(),uses=new HashMap<>();
        for(var block:function.blocks())for(var instruction:block.instructions()) {
            var result=IrValueUses.result(instruction);
            if(result!=null)definitions.merge(result.name(),1,Integer::sum);
            for(var operand:IrValueUses.inputs(instruction))if(operand instanceof IrTemporary temporary)
                uses.merge(temporary.name(),1,Integer::sum);
        }
        var flow=IrControlFlow.analyze(function);var planned=new HashMap<String,Map<Integer,Fusion>>();
        for(var block:function.blocks())if(flow.reachable().contains(block.label())) {
            var code=flow.effectiveInstructions(block.label());var matches=new HashMap<Integer,Fusion>();
            for(int i=0;i<code.size();i++) {
                if(!(code.get(i) instanceof IrBinaryInstruction comparison) || !isComparison(comparison.operator())
                        || comparison.left().type()!=comparison.right().type()
                        || !comparison.result().type().isIntegerScalar()
                        || !single(comparison.result(),definitions,uses))continue;
                int next=i+1;IrTemporary condition=comparison.result();boolean inverted=false;
                var discarded=new ArrayList<IrTemporary>();discarded.add(condition);
                while(next<code.size()) {
                    if(code.get(next) instanceof IrCastInstruction cast && cast.result().type()==IrType.BOOL
                            && cast.value().equals(condition) && single(cast.result(),definitions,uses)) {
                        condition=cast.result();
                    } else if(code.get(next) instanceof IrUnaryInstruction unary && unary.operator()==IrUnaryOperator.LOGICAL_NOT
                            && unary.result().type().isIntegerScalar() && unary.operand().equals(condition)
                            && single(unary.result(),definitions,uses)) {
                        condition=unary.result();inverted=!inverted;
                    } else break;
                    discarded.add(condition);next++;
                }
                if(next<code.size() && code.get(next) instanceof IrBranchInstruction branch && branch.condition().equals(condition)) {
                    // Swapping targets preserves unordered floating comparisons: !(a<b) is not a>=b for NaN.
                    var targets=inverted?new IrBranchInstruction(branch.condition(),branch.elseLabel(),branch.thenLabel(),branch.range()):branch;
                    matches.put(i,new Fusion(comparison,targets,next-i+1,discarded));i=next;
                }
            }
            if(!matches.isEmpty())planned.put(block.label(),Map.copyOf(matches));
        }
        groups=Map.copyOf(planned);
    }

    private static boolean single(IrTemporary value,Map<String,Integer> definitions,Map<String,Integer> uses) {
        return definitions.getOrDefault(value.name(),0)==1 && uses.getOrDefault(value.name(),0)==1;
    }
    private static boolean isComparison(IrBinaryOperator operator) {
        return switch(operator){case EQUAL,NOT_EQUAL,LESS_THAN,LESS_EQUAL,GREATER_THAN,GREATER_EQUAL->true;default->false;};
    }
    public static ComparisonBranchPlan analyze(IrFunction function){return new ComparisonBranchPlan(Objects.requireNonNull(function));}
    /** A match never crosses a block, terminator, call, or any intervening instruction. */
    public Fusion at(String block,int instructionIndex){return groups.getOrDefault(block,Map.of()).get(instructionIndex);}
}
