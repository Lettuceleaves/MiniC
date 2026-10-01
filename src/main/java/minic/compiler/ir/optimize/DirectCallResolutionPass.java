package minic.compiler.ir.optimize;

import minic.compiler.ir.IrResult;
import minic.compiler.ir.instruction.CallInstruction.*;
import minic.compiler.ir.instruction.IrInstruction;
import minic.compiler.ir.manager.IrTypeLowerer;
import minic.compiler.ir.model.*;
import minic.compiler.ir.value.IrValue.*;
import java.util.*;

/** Native-only canonicalization of calls whose function address is already known. */
public final class DirectCallResolutionPass implements IrPass {
    @Override public String name() { return "known-function-call-resolution"; }
    @Override public IrResult apply(IrResult input) {
        Objects.requireNonNull(input);
        var definitions = new HashMap<String,IrFunction>();
        input.functions().forEach(function -> definitions.put(function.name(),function));
        var functions = new ArrayList<IrFunction>();
        boolean changed = false;
        for(var function:input.functions()) {
            var blocks = new ArrayList<IrBlock>();
            boolean functionChanged = false;
            for(var block:function.blocks()) {
                var instructions = new ArrayList<IrInstruction>();
                boolean blockChanged = false;
                for(var instruction:block.instructions()) {
                    if(instruction instanceof IrIndirectCallInstruction call
                            && call.calleeAddress() instanceof IrFunctionAddress address
                            && compatible(call,address.functionName(),definitions,input.externalFunctionNames())) {
                        instructions.add(new IrCallInstruction(call.result(),address.functionName(),call.arguments(),call.variadic(),call.range()));
                        blockChanged = true;
                    } else instructions.add(instruction);
                }
                blocks.add(blockChanged ? new IrBlock(block.label(),instructions) : block);
                functionChanged |= blockChanged;
            }
            functions.add(functionChanged ? new IrFunction(function.name(),function.returnType(),function.parameters(),
                    function.variadic(),blocks,function.range()) : function);
            changed |= functionChanged;
        }
        return !changed ? input : new IrResult(functions,input.stringData(),input.globalData(),input.externalFunctionNames(),
                input.externalObjectNames(),input.structLayouts(),input.currentAstNode(),input.currentSubject(),input.displayNames(),input.entryFunction());
    }

    private static boolean compatible(IrIndirectCallInstruction call,String name,Map<String,IrFunction> definitions,Set<String> externals) {
        var target = definitions.get(name);
        if(target == null) return externals.contains(name);
        int count = target.parameters().size();
        // A source function-pointer cast can describe a different ABI. Keep that
        // existing indirect boundary instead of assigning it a stronger signature.
        if(call.variadic() != target.variadic() || call.arguments().size() < count
                || (!target.variadic() && call.arguments().size() != count)) return false;
        for(int i=0;i<count;i++) {
            var expected = target.parameters().get(i).type();
            var value = call.arguments().get(i);
            if(value.type() != expected && !(expected == IrType.POINTER && value instanceof IrConstant constant
                    && constant.type().isIntegerScalar() && constant.value() == 0)) return false;
        }
        return call.result() == null || (!target.returnType().isVoid() && call.result().type() == IrTypeLowerer.lower(target.returnType()));
    }
}
