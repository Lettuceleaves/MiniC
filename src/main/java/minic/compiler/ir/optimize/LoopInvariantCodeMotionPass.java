package minic.compiler.ir.optimize;

import minic.compiler.ir.IrResult;
import minic.compiler.ir.instruction.CallInstruction.*;
import minic.compiler.ir.instruction.ComputeInstruction.IrBinaryInstruction;
import minic.compiler.ir.instruction.ControlInstruction.IrJumpInstruction;
import minic.compiler.ir.instruction.IrInstruction;
import minic.compiler.ir.model.*;
import minic.compiler.ir.value.IrValue;
import minic.compiler.ir.value.IrValue.*;

import java.util.*;

/**
 * Native-only motion of nontrapping integer computations into an existing natural-loop
 * preheader. Non-SSA definitions are counted across the whole function, including dead tails.
 * Calls, overlapping loops, memory operations and live parameter slots are deliberately excluded.
 */
public final class LoopInvariantCodeMotionPass implements IrPass {
    @Override public String name() { return "loop-invariant-code-motion"; }
    @Override public IrResult apply(IrResult input) {
        Objects.requireNonNull(input,"input");
        var functions=input.functions().stream().map(this::optimize).toList();
        return functions.equals(input.functions())?input:new IrResult(functions,input.stringData(),input.globalData(),
                input.externalFunctionNames(),input.externalObjectNames(),input.structLayouts(),input.currentAstNode(),
                input.currentSubject(),input.displayNames(), input.entryFunction());
    }

    private IrFunction optimize(IrFunction function) {
        if(function.blocks().isEmpty())return function;
        var flow=IrControlFlow.analyze(function);
        var dominators=dominators(function,flow);
        var loops=naturalLoops(flow,dominators);
        IrFunction current=function;
        for(var loop:loops) {
            // Keep the first slice simple: nested and irreducibly overlapping loops retain
            // their original IR. Independent loops can still be optimized separately.
            if(loops.stream().anyMatch(other->other!=loop&&!Collections.disjoint(loop.blocks(),other.blocks())))continue;
            current=hoist(current,loop,dominators);
        }
        return current;
    }

    private IrFunction hoist(IrFunction function,Loop loop,Map<String,Set<String>> dominators) {
        var flow=IrControlFlow.analyze(function);
        var incoming=flow.predecessors(loop.header()).stream().filter(flow.reachable()::contains)
                .filter(label->!loop.blocks().contains(label)).toList();
        if(incoming.size()!=1)return function;
        String preheader=incoming.getFirst();
        var before=flow.effectiveInstructions(preheader);
        if(before.isEmpty()||!(before.getLast() instanceof IrJumpInstruction jump)||!jump.targetLabel().equals(loop.header()))return function;
        for(String label:loop.blocks())for(var instruction:flow.effectiveInstructions(label))
            if(instruction instanceof IrCallInstruction||instruction instanceof IrIndirectCallInstruction)return function;

        var definitions=new HashMap<String,Definition>();
        var counts=new HashMap<String,Integer>();
        for(var block:function.blocks()) {
            int prefix=flow.effectiveInstructions(block.label()).size();
            for(int index=0;index<block.instructions().size();index++) {
                var result=IrValueUses.result(block.instructions().get(index));
                if(result==null)continue;
                counts.merge(result.name(),1,Integer::sum);
                definitions.put(result.name(),new Definition(block.label(),index<prefix&&flow.reachable().contains(block.label())));
            }
        }
        var moved=new LinkedHashMap<String,IrInstruction>();
        boolean changed;
        do {
            changed=false;
            for(var block:function.blocks())if(loop.blocks().contains(block.label())) {
                for(var instruction:flow.effectiveInstructions(block.label())) {
                    if(!(instruction instanceof IrBinaryInstruction binary)||!pureInteger(binary)
                            ||counts.getOrDefault(binary.result().name(),0)!=1||moved.containsKey(binary.result().name()))continue;
                    if(available(binary.left(),preheader,loop,definitions,counts,dominators,moved)
                            &&available(binary.right(),preheader,loop,definitions,counts,dominators,moved)) {
                        moved.put(binary.result().name(),binary); changed=true;
                    }
                }
            }
        }while(changed);
        if(moved.isEmpty())return function;

        var blocks=new ArrayList<IrBlock>();
        for(var block:function.blocks()) {
            var code=new ArrayList<IrInstruction>();
            for(int index=0;index<block.instructions().size();index++) {
                if(block.label().equals(preheader)&&index==before.size()-1)code.addAll(moved.values());
                var instruction=block.instructions().get(index);
                var result=IrValueUses.result(instruction);
                if(result==null||!moved.containsKey(result.name()))code.add(instruction);
            }
            blocks.add(new IrBlock(block.label(),code));
        }
        return new IrFunction(function.name(),function.returnType(),function.parameters(),function.variadic(),blocks,function.range());
    }

    private static boolean available(IrValue value,String preheader,Loop loop,Map<String,Definition> definitions,
                                     Map<String,Integer> counts,Map<String,Set<String>> dominators,Map<String,IrInstruction> moved) {
        if(value instanceof IrConstant)return true;
        if(!(value instanceof IrTemporary temporary))return false; // Parameter references are mutable slots.
        if(moved.containsKey(temporary.name()))return true;
        var definition=definitions.get(temporary.name());
        return counts.getOrDefault(temporary.name(),0)==1&&definition!=null&&definition.executable()
                &&!loop.blocks().contains(definition.block())&&dominators.get(preheader).contains(definition.block());
    }

    private static boolean pureInteger(IrBinaryInstruction binary) {
        IrType type=binary.left().type();
        if(!type.isIntegerScalar()||type.sizeBytes()<4||binary.right().type()!=type)return false;
        return switch(binary.operator()) {
            case ADD,SUBTRACT,MULTIPLY,BITWISE_AND,BITWISE_OR,BITWISE_XOR->binary.result().type()==type;
            case EQUAL,NOT_EQUAL,LESS_THAN,LESS_EQUAL,GREATER_THAN,GREATER_EQUAL->binary.result().type().isIntegerScalar();
            default->false;
        };
    }

    private static Map<String,Set<String>> dominators(IrFunction function,IrControlFlow flow) {
        String entry=function.blocks().getFirst().label();
        var result=new LinkedHashMap<String,Set<String>>();
        for(String label:flow.reachable())result.put(label,label.equals(entry)?Set.of(entry):Set.copyOf(flow.reachable()));
        boolean changed;
        do {
            changed=false;
            for(String label:flow.reachable())if(!label.equals(entry)) {
                var next=new HashSet<>(flow.reachable());
                for(String predecessor:flow.predecessors(label))if(flow.reachable().contains(predecessor))next.retainAll(result.get(predecessor));
                next.add(label);
                if(!next.equals(result.put(label,next)))changed=true;
            }
        }while(changed);
        return result;
    }

    private static List<Loop> naturalLoops(IrControlFlow flow,Map<String,Set<String>> dominators) {
        var byHeader=new LinkedHashMap<String,Set<String>>();
        for(String tail:flow.reachable())for(String header:flow.successors(tail)) {
            if(!dominators.get(tail).contains(header))continue;
            var members=new LinkedHashSet<String>(); members.add(header);
            var pending=new ArrayDeque<String>(); pending.add(tail);
            while(!pending.isEmpty()) {
                String label=pending.removeFirst();
                if(!members.add(label))continue;
                for(String predecessor:flow.predecessors(label))if(flow.reachable().contains(predecessor))pending.add(predecessor);
            }
            if(members.stream().allMatch(label->dominators.get(label).contains(header)))
                byHeader.computeIfAbsent(header,ignored->new LinkedHashSet<>()).addAll(members);
        }
        return byHeader.entrySet().stream().map(entry->new Loop(entry.getKey(),Set.copyOf(entry.getValue()))).toList();
    }

    private record Definition(String block,boolean executable) { }
    private record Loop(String header,Set<String> blocks) { }
}
