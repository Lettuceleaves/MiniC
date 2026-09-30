package minic.compiler.semantic.manager;

import minic.compiler.parser.node.Declaration.FunctionDecl;
import minic.compiler.parser.node.Declaration.Parameter;
import minic.compiler.parser.node.Declaration.Program;
import minic.compiler.parser.node.Expression.CallExpr;
import minic.compiler.type.MiniType;
import minic.compiler.semantic.model.Scope;
import minic.compiler.semantic.model.Symbol;
import minic.compiler.semantic.model.Symbol.SymbolKind;
import minic.compiler.Diagnostic;
import minic.SourceRange;

import java.util.List;

public final class FunctionRegistry {
    private final Scope globalScope;
    private final List<Diagnostic> diagnostics;
    private final java.util.Map<String, FunctionState> functionStates = new java.util.HashMap<>();

    public FunctionRegistry(Scope globalScope, List<Diagnostic> diagnostics) {
        this.globalScope = globalScope;
        this.diagnostics = diagnostics;
    }

    public void defineFunctions(Program program) {
        for (FunctionDecl functionDecl : program.functions()) {
            validateFunctionName(functionDecl);
            validateFunctionSignature(functionDecl);
            String name = functionDecl.name();
            List<MiniType> parameterTypes = parameterTypes(functionDecl);
            MiniType returnType = functionDecl.returnType().unqualified();
            FunctionState existingState = functionStates.get(name);
            if (existingState == null) {
                Symbol symbol = new Symbol(
                        name,
                        SymbolKind.FUNCTION,
                        functionDecl.range(),
                        MiniType.function(returnType, parameterTypes, functionDecl.variadic()),
                        parameterTypes.size()
                );
                globalScope.define(symbol);
                functionStates.put(name, new FunctionState(
                        returnType,
                        parameterTypes,
                        functionDecl.variadic(),
                        functionDecl.hasBody(),
                        functionDecl.external(),
                        functionDecl.noReturn()
                ));
                if (functionDecl.external() && functionDecl.hasBody()) {
                    report(functionDecl.range(), "外部函数不能携带函数体：" + name);
                }
                continue;
            }
            if (!existingState.returnType().equals(returnType)
                    || !existingState.parameterTypes().equals(parameterTypes)) {
                report(functionDecl.range(), "函数声明签名不一致：" + name);
                continue;
            }
            if (existingState.variadic() != functionDecl.variadic()) {
                report(functionDecl.range(), "函数声明签名不一致：" + name);
                continue;
            }
            if (functionDecl.external() && functionDecl.hasBody()) {
                report(functionDecl.range(), "外部函数不能携带函数体：" + name);
                continue;
            }
            if (functionDecl.hasBody()) {
                if (existingState.defined()) {
                    report(functionDecl.range(), "重复函数定义：" + functionSignature(functionDecl));
                } else {
                    functionStates.put(name, existingState.asDefined().withNoReturn(functionDecl.noReturn()));
                }
            } else if (functionDecl.external() && !existingState.external()) {
                functionStates.put(name, existingState.asExternal().withNoReturn(functionDecl.noReturn()));
            } else if (functionDecl.noReturn() && !existingState.noReturn()) {
                functionStates.put(name, existingState.withNoReturn(true));
            }
        }
    }

    public void validateMain(Program program) {
        FunctionState mainState = functionStates.get("main");
        if (mainState == null) {
            report(program.range(), "缺少 main 函数");
        } else if (!mainState.defined()) {
            report(program.range(), "缺少 main 函数定义");
        }
    }

    MiniType resolveFunction(CallExpr callExpr, List<MiniType> argumentTypes) {
        if (!callExpr.hasDirectCalleeName()) {
            throw new IllegalArgumentException("direct function resolution requires a named callee");
        }
        var functionSymbol = globalScope.resolve(callExpr.calleeName())
                .filter(symbol -> symbol.kind() == SymbolKind.FUNCTION);
        if (functionSymbol.isEmpty()) {
            report(callExpr.range(), "未解析函数调用：" + callExpr.calleeName());
            return MiniType.INT;
        }
        FunctionState functionState = functionStates.get(callExpr.calleeName());
        if (functionState != null && !functionState.defined() && !functionState.external()) {
            report(callExpr.range(), "未定义函数调用：" + callExpr.calleeName());
        }
        Integer arity = functionSymbol.orElseThrow().arity();
        boolean variadic = functionState != null && functionState.variadic();
        if (arity != null && ((!variadic && arity != callExpr.arguments().size())
                || (variadic && callExpr.arguments().size() < arity))) {
            report(callExpr.range(), "函数调用实参数量不匹配：" + callExpr.calleeName());
        }
        if (functionState != null && functionState.parameterTypes().size() <= argumentTypes.size()) {
            for (int index = 0; index < functionState.parameterTypes().size(); index++) {
                MiniType parameterType = functionState.parameterTypes().get(index);
                MiniType argumentType = argumentTypes.get(index);
                if (!isArgumentCompatible(parameterType, argumentType)) {
                    report(
                            callExpr.arguments().get(index).range(),
                            "函数调用实参类型不匹配：" + callExpr.calleeName()
                    );
                }
            }
        }
        return functionSymbol.orElseThrow().type().returnType();
    }

    MiniType resolveFunctionAddress(String name, minic.SourceRange range) {
        var functionSymbol = globalScope.resolve(name)
                .filter(symbol -> symbol.kind() == SymbolKind.FUNCTION);
        if (functionSymbol.isEmpty()) {
            report(range, "未解析变量：" + name);
            return MiniType.INT;
        }
        FunctionState functionState = functionStates.get(name);
        if (functionState != null && !functionState.defined() && !functionState.external()) {
            report(range, "未定义函数取址：" + name);
        }
        return functionSymbol.orElseThrow().type().pointerTo();
    }

    boolean isNoReturn(String name) {
        FunctionState state = functionStates.get(name);
        return state != null && state.noReturn();
    }

    private boolean isArgumentCompatible(MiniType parameterType, MiniType argumentType) {
        return TypeCompatibility.isArgumentCompatible(parameterType, argumentType);
    }

    private void validateFunctionName(FunctionDecl functionDecl) {
        String name = functionDecl.name();
        if (!isValidFunctionName(name)) {
            report(functionDecl.range(), "非法函数名：" + name);
        }
    }

    private boolean isValidFunctionName(String name) {
        if (name.isEmpty() || name.charAt(0) == '_') {
            return false;
        }
        if (!isAsciiLetter(name.charAt(0))) {
            return false;
        }
        for (int index = 1; index < name.length(); index++) {
            char character = name.charAt(index);
            if (!isAsciiLetter(character) && !isAsciiDigit(character) && character != '_') {
                return false;
            }
        }
        return true;
    }

    private boolean isAsciiLetter(char character) {
        return (character >= 'a' && character <= 'z') || (character >= 'A' && character <= 'Z');
    }

    private boolean isAsciiDigit(char character) {
        return character >= '0' && character <= '9';
    }

    private void validateFunctionSignature(FunctionDecl functionDecl) {
        if ("main".equals(functionDecl.name())) {
            if (!functionDecl.parameters().isEmpty()) {
                report(functionDecl.range(), "非法 main 函数签名：main 必须无参数");
            }
            if (!functionDecl.returnType().unqualified().equals(MiniType.INT)) {
                report(functionDecl.range(), "非法 main 函数签名：main 必须返回 int");
            }
        }
    }

    private String functionSignature(FunctionDecl functionDecl) {
        return functionDecl.name() + "/" + functionDecl.parameters().size();
    }

    private List<MiniType> parameterTypes(FunctionDecl functionDecl) {
        return functionDecl.parameters().stream()
                .map(Parameter::type)
                .map(MiniType::unqualified)
                .toList();
    }

    private void report(SourceRange range, String message) {
        diagnostics.add(new Diagnostic("SEM001", Diagnostic.Severity.ERROR, message, range));
    }

    private record FunctionState(
            MiniType returnType,
            List<MiniType> parameterTypes,
            boolean variadic,
            boolean defined,
            boolean external,
            boolean noReturn
    ) {
        private FunctionState {
            java.util.Objects.requireNonNull(returnType, "returnType");
            parameterTypes = List.copyOf(parameterTypes);
        }

        private FunctionState asDefined() {
            return new FunctionState(returnType, parameterTypes, variadic, true, external, noReturn);
        }

        private FunctionState asExternal() {
            return new FunctionState(returnType, parameterTypes, variadic, defined, true, noReturn);
        }

        private FunctionState withNoReturn(boolean declaredNoReturn) {
            return new FunctionState(returnType, parameterTypes, variadic, defined, external,
                    noReturn || declaredNoReturn);
        }
    }
}
