package minic.compiler.semantic;

import minic.compiler.ast.decl.Program;
import minic.compiler.ast.decl.StructDecl;
import minic.compiler.ast.decl.StructField;
import minic.compiler.type.MiniType;
import minic.compiler.type.TypeLayout;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

final class StructRegistry {
    private final Scope globalScope;
    private final SemanticReporter reporter;
    private final Map<String, StructDecl> structDecls = new LinkedHashMap<>();
    private final Map<String, StructLayout> structLayouts = new LinkedHashMap<>();

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
            } else {
                structDecls.put(structDecl.name(), structDecl);
            }
            validateFields(structDecl);
        }
    }

    void validateProgramTypes(Program program) {
        program.structs().forEach(this::validateStructFieldTypes);
        validateRecursiveStructValues();
        program.functions().forEach(functionDecl -> {
            validateDeclaredType(functionDecl.returnType(), functionDecl.range());
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

    Map<String, StructLayout> computeLayouts() {
        structLayouts.clear();
        for (StructDecl structDecl : structDecls.values()) {
            layoutOf(structDecl.name());
        }
        return Map.copyOf(structLayouts);
    }

    java.util.Optional<StructFieldLayout> field(MiniType type, String fieldName) {
        if (type instanceof MiniType.StructType structType) {
            StructLayout layout = layoutOf(structType.name());
            if (layout != null) {
                return layout.field(fieldName);
            }
        }
        return java.util.Optional.empty();
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
            if (directStructName(field.type()).filter(structDecl.name()::equals).isPresent()) {
                reporter.report(field.range(), "结构体字段不能直接包含自身：" + structDecl.name());
            }
        }
    }

    private java.util.Optional<String> directStructName(MiniType type) {
        if (type instanceof MiniType.StructType structType) {
            return java.util.Optional.of(structType.name());
        }
        if (type.isArray()) {
            return directStructName(type.elementType());
        }
        return java.util.Optional.empty();
    }

    private void validateRecursiveStructValues() {
        Set<String> reportedStructs = new HashSet<>();
        for (StructDecl structDecl : structDecls.values()) {
            detectRecursiveStructValue(structDecl.name(), structDecl.name(), new HashSet<>(), reportedStructs);
        }
    }

    private boolean detectRecursiveStructValue(
            String rootName,
            String currentName,
            Set<String> visiting,
            Set<String> reportedStructs
    ) {
        if (!visiting.add(currentName)) {
            return currentName.equals(rootName);
        }
        StructDecl currentDecl = structDecls.get(currentName);
        if (currentDecl == null) {
            visiting.remove(currentName);
            return false;
        }
        for (StructField field : currentDecl.fields()) {
            java.util.Optional<String> nestedStructName = directStructName(field.type());
            if (nestedStructName.isEmpty()) {
                continue;
            }
            String nestedName = nestedStructName.orElseThrow();
            if (nestedName.equals(currentName)) {
                continue;
            }
            if (nestedName.equals(rootName) || detectRecursiveStructValue(rootName, nestedName, visiting, reportedStructs)) {
                if (reportedStructs.add(rootName)) {
                    reporter.report(field.range(), "结构体字段形成递归值包含：" + rootName);
                }
                visiting.remove(currentName);
                return true;
            }
        }
        visiting.remove(currentName);
        return false;
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

    private StructLayout layoutOf(String structName) {
        StructLayout existingLayout = structLayouts.get(structName);
        if (existingLayout != null) {
            return existingLayout;
        }
        StructDecl structDecl = structDecls.get(structName);
        if (structDecl == null) {
            return null;
        }

        ArrayList<StructFieldLayout> fieldLayouts = new ArrayList<>();
        int offset = 0;
        int structAlignment = 1;
        for (StructField field : structDecl.fields()) {
            int fieldAlignment = alignmentOf(field.type());
            int fieldSize = sizeOf(field.type());
            offset = alignTo(offset, fieldAlignment);
            fieldLayouts.add(new StructFieldLayout(field.name(), field.type(), offset, fieldSize, fieldAlignment));
            offset += fieldSize;
            if (fieldAlignment > structAlignment) {
                structAlignment = fieldAlignment;
            }
        }
        int structSize = alignTo(offset == 0 ? 1 : offset, structAlignment);
        StructLayout layout = new StructLayout(structName, structSize, structAlignment, fieldLayouts);
        structLayouts.put(structName, layout);
        return layout;
    }

    private int sizeOf(MiniType type) {
        if (type.isArray()) {
            return sizeOf(type.elementType()) * type.arrayLength();
        }
        if (type.isPointer()) {
            return TypeLayout.sizeOf(type);
        }
        if (type instanceof MiniType.StructType structType) {
            StructLayout layout = layoutOf(structType.name());
            return layout != null ? layout.size() : 1;
        }
        return TypeLayout.sizeOf(type);
    }

    private int alignmentOf(MiniType type) {
        if (type.isArray()) {
            return alignmentOf(type.elementType());
        }
        if (type.isPointer()) {
            return TypeLayout.alignmentOf(type);
        }
        if (type instanceof MiniType.StructType structType) {
            StructLayout layout = layoutOf(structType.name());
            return layout != null ? layout.alignment() : 1;
        }
        return TypeLayout.alignmentOf(type);
    }

    private int alignTo(int value, int alignment) {
        int remainder = value - value / alignment * alignment;
        if (remainder == 0) {
            return value;
        }
        return value + alignment - remainder;
    }
}
