package minic.compiler.semantic;

import minic.compiler.ast.decl.FunctionDecl;
import minic.compiler.ast.decl.Program;
import minic.compiler.ast.expr.CallExpr;
import minic.compiler.type.MiniType;

final class FunctionRegistry {
    private final Scope globalScope;
    private final SemanticReporter reporter;
    private final java.util.Map<String, FunctionState> functionStates = new java.util.HashMap<>();

    FunctionRegistry(Scope globalScope, SemanticReporter reporter) {
        this.globalScope = globalScope;
        this.reporter = reporter;
    }

    void defineFunctions(Program program) {
        for (FunctionDecl functionDecl : program.functions()) {
            validateFunctionName(functionDecl);
            validateFunctionSignature(functionDecl);
            String name = functionDecl.name();
            int arity = functionDecl.parameters().size();
            FunctionState existingState = functionStates.get(name);
            if (existingState == null) {
                Symbol symbol = new Symbol(name, SymbolKind.FUNCTION, functionDecl.range(), arity);
                globalScope.define(symbol);
                functionStates.put(name, new FunctionState(arity, functionDecl.hasBody(), functionDecl.external()));
                if (functionDecl.external() && functionDecl.hasBody()) {
                    reporter.report(functionDecl.range(), "外部函数不能携带函数体：" + name);
                }
                continue;
            }
            if (existingState.arity() != arity) {
                reporter.report(functionDecl.range(), "函数声明签名不一致：" + name);
                continue;
            }
            if (functionDecl.external() && functionDecl.hasBody()) {
                reporter.report(functionDecl.range(), "外部函数不能携带函数体：" + name);
                continue;
            }
            if (functionDecl.hasBody()) {
                if (existingState.defined()) {
                    reporter.report(functionDecl.range(), "重复函数定义：" + functionSignature(functionDecl));
                } else {
                    functionStates.put(name, existingState.asDefined());
                }
            } else if (functionDecl.external() && !existingState.external()) {
                functionStates.put(name, existingState.asExternal());
            }
        }
    }

    void validateMain(Program program) {
        FunctionState mainState = functionStates.get("main");
        if (mainState == null) {
            reporter.report(program.range(), "缺少 main 函数");
        } else if (!mainState.defined()) {
            reporter.report(program.range(), "缺少 main 函数定义");
        }
    }

    MiniType resolveFunction(CallExpr callExpr) {
        var functionSymbol = globalScope.resolve(callExpr.calleeName())
                .filter(symbol -> symbol.kind() == SymbolKind.FUNCTION);
        if (functionSymbol.isEmpty()) {
            reporter.report(callExpr.range(), "未解析函数调用：" + callExpr.calleeName());
            return MiniType.INT;
        }
        FunctionState functionState = functionStates.get(callExpr.calleeName());
        if (functionState != null && !functionState.defined() && !functionState.external()) {
            reporter.report(callExpr.range(), "未定义函数调用：" + callExpr.calleeName());
        }
        Integer arity = functionSymbol.orElseThrow().arity();
        if (arity != null && arity != callExpr.arguments().size()) {
            reporter.report(callExpr.range(), "函数调用实参数量不匹配：" + callExpr.calleeName());
        }
        return functionSymbol.orElseThrow().type();
    }

    private void validateFunctionName(FunctionDecl functionDecl) {
        String name = functionDecl.name();
        if (!isValidFunctionName(name)) {
            reporter.report(functionDecl.range(), "非法函数名：" + name);
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
        if ("main".equals(functionDecl.name()) && !functionDecl.parameters().isEmpty()) {
            reporter.report(functionDecl.range(), "非法 main 函数签名：main 必须无参数");
        }
    }

    private String functionSignature(FunctionDecl functionDecl) {
        return functionDecl.name() + "/" + functionDecl.parameters().size();
    }

    private record FunctionState(int arity, boolean defined, boolean external) {
        private FunctionState asDefined() {
            return new FunctionState(arity, true, external);
        }

        private FunctionState asExternal() {
            return new FunctionState(arity, defined, true);
        }
    }
}
