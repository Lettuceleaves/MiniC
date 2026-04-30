package minic.compiler.semantic;

import minic.compiler.ast.decl.Program;
import minic.compiler.ast.decl.StructDecl;
import minic.compiler.ast.decl.StructField;
import minic.compiler.type.MiniType;

import java.util.HashSet;
import java.util.Set;

final class StructRegistry {
    private final Scope globalScope;
    private final SemanticReporter reporter;

    StructRegistry(Scope globalScope, SemanticReporter reporter) {
        this.globalScope = globalScope;
        this.reporter = reporter;
    }

    void defineStructs(Program program) {
        for (StructDecl structDecl : program.structs()) {
            Symbol symbol = new Symbol(
                    structDecl.name(),
                    SymbolKind.STRUCT,
                    structDecl.range(),
                    MiniType.struct(structDecl.name()),
                    null
            );
            if (!globalScope.define(symbol)) {
                reporter.report(structDecl.range(), "重复结构体定义：" + structDecl.name());
            }
            validateFields(structDecl);
        }
    }

    void validateProgramTypes(Program program) {
        program.structs().forEach(this::validateStructFieldTypes);
        program.functions().forEach(functionDecl -> {
            functionDecl.parameters().forEach(parameter -> validateDeclaredType(parameter.type(), parameter.range()));
            functionDecl.bodyOptional().ifPresent(body -> {
                // 局部声明会在语句语义分析阶段校验。
            });
        });
    }

    void validateDeclaredType(MiniType type, minic.source.SourceRange range) {
        MiniType baseType = unwrap(type);
        if (baseType instanceof MiniType.StructType structType
                && globalScope.resolve(structType.name())
                .filter(symbol -> symbol.kind() == SymbolKind.STRUCT)
                .isEmpty()) {
            reporter.report(range, "未声明结构体类型：" + structType.name());
        }
    }

    private void validateFields(StructDecl structDecl) {
        Set<String> fieldNames = new HashSet<>();
        for (StructField field : structDecl.fields()) {
            if (!fieldNames.add(field.name())) {
                reporter.report(field.range(), "重复结构体字段：" + field.name());
            }
        }
    }

    private void validateStructFieldTypes(StructDecl structDecl) {
        for (StructField field : structDecl.fields()) {
            validateDeclaredType(field.type(), field.range());
        }
    }

    private MiniType unwrap(MiniType type) {
        if (type.isArray()) {
            return unwrap(type.elementType());
        }
        if (type.isPointer()) {
            return unwrap(type.pointee());
        }
        return type;
    }
}
