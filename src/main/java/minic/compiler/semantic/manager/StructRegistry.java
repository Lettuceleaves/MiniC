package minic.compiler.semantic.manager;

import minic.compiler.parser.node.Declaration.Program;
import minic.compiler.parser.node.Declaration.StructDecl;
import minic.compiler.parser.node.Declaration.StructField;
import minic.compiler.type.MiniType;
import minic.compiler.type.TypeLayout;
import minic.compiler.semantic.model.Scope;
import minic.compiler.semantic.model.StructLayout;
import minic.compiler.semantic.model.StructLayout.StructFieldLayout;
import minic.compiler.semantic.model.Symbol;
import minic.compiler.semantic.model.Symbol.SymbolKind;
import minic.diagnostics.Diagnostic;
import minic.source.SourceRange;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class StructRegistry {
    private final Scope globalScope;
    private final List<Diagnostic> diagnostics;
    private final Map<String, StructDecl> structDecls = new LinkedHashMap<>();
    private final Map<String, StructLayout> structLayouts = new LinkedHashMap<>();
    private final Set<String> layoutsInProgress = new HashSet<>();

    public StructRegistry(Scope globalScope, List<Diagnostic> diagnostics) {
        this.globalScope = globalScope;
        this.diagnostics = diagnostics;
    }

    public void defineStructs(Program program) {
        for (StructDecl structDecl : program.structs()) {
            if (!structDecl.definition()) {
                if (globalScope.resolve(structDecl.name()).isEmpty()) {
                    globalScope.define(new Symbol(structDecl.name(), SymbolKind.STRUCT, structDecl.range(),
                            MiniType.struct(structDecl.name()), null));
                }
                continue;
            }
            Symbol symbol = new Symbol(
                    structDecl.name(),
                    SymbolKind.STRUCT,
                    structDecl.range(),
                    MiniType.struct(structDecl.name()),
                    null
            );
            if (structDecls.containsKey(structDecl.name())) {
                report(structDecl.range(), "重复结构体定义：" + structDecl.name());
            } else {
                if (globalScope.resolve(structDecl.name()).isEmpty()) globalScope.define(symbol);
                structDecls.put(structDecl.name(), structDecl);
            }
            validateFields(structDecl);
        }
    }

    public void validateProgramTypes(Program program) {
        program.structs().stream().filter(StructDecl::definition).forEach(this::validateStructFieldTypes);
        validateRecursiveStructValues();
        program.functions().forEach(functionDecl -> {
            validateFunctionReturnType(functionDecl.returnType(), functionDecl.range());
            functionDecl.parameters().forEach(parameter -> validateDeclaredType(parameter.type(), parameter.range()));
            functionDecl.bodyOptional().ifPresent(body -> {
                // 局部声明会在语句语义分析阶段校验。
            });
        });
    }

    void validateDeclaredType(MiniType type, minic.source.SourceRange range) {
        validateTypeReferences(type, range);
        validateTypeShape(type, range, true);
    }

    private void validateFunctionReturnType(MiniType type, SourceRange range) {
        validateTypeReferences(type, range);
        if (type.isArray() || type.isFunction()) {
            report(range, "函数不能直接返回数组或函数类型");
        }
        validateTypeShape(type, range, false);
    }

    private void validateTypeReferences(MiniType type, SourceRange range) {
        if (type instanceof MiniType.StructType structType
                && globalScope.resolve(structType.name())
                .filter(symbol -> symbol.kind() == SymbolKind.STRUCT)
                .isEmpty()) {
            report(range, "未声明结构体类型：" + structType.name());
            return;
        }
        if (type instanceof MiniType.PointerType pointerType) {
            validateTypeReferences(pointerType.pointee(), range);
        } else if (type instanceof MiniType.ArrayType arrayType) {
            validateTypeReferences(arrayType.elementType(), range);
        } else if (type instanceof MiniType.FunctionType functionType) {
            validateTypeReferences(functionType.returnType(), range);
            functionType.parameterTypes().forEach(parameterType -> validateTypeReferences(parameterType, range));
        }
    }

    private void validateTypeShape(MiniType type, SourceRange range, boolean objectRoot) {
        if (objectRoot && type.isVoid()) {
            report(range, "对象不能声明为 void 类型");
        }
        if (objectRoot && type.isFunction()) {
            report(range, "对象不能直接声明为函数类型；请使用函数指针");
        }
        if (type instanceof MiniType.ArrayType arrayType) {
            if (arrayType.elementType().isFunction() || arrayType.elementType().isVoid()) {
                report(range, "数组元素不能是函数或 void 类型");
            }
            validateTypeShape(arrayType.elementType(), range, false);
        } else if (type instanceof MiniType.PointerType pointerType) {
            validateTypeShape(pointerType.pointee(), range, false);
        } else if (type instanceof MiniType.FunctionType functionType) {
            if (functionType.returnType().isArray() || functionType.returnType().isFunction()) {
                report(range, "函数不能直接返回数组或函数类型");
            }
            validateTypeShape(functionType.returnType(), range, false);
            functionType.parameterTypes().forEach(parameterType -> validateTypeShape(parameterType, range, true));
        }
    }

    public Map<String, StructLayout> computeLayouts() {
        structLayouts.clear();
        layoutsInProgress.clear();
        for (StructDecl structDecl : structDecls.values()) {
            layoutOf(structDecl.name());
        }
        return Map.copyOf(structLayouts);
    }

    boolean hasLayout(String structName) {
        return structLayouts.containsKey(structName) || structDecls.containsKey(structName);
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

    java.util.List<StructFieldLayout> fields(String structName) {
        StructLayout layout = layoutOf(structName);
        return layout != null ? layout.fields() : null;
    }

    private void validateFields(StructDecl structDecl) {
        Set<String> fieldNames = new HashSet<>();
        for (StructField field : structDecl.fields()) {
            if (!fieldNames.add(field.name())) {
                report(field.range(), "重复结构体字段：" + field.name());
            }
        }
    }

    private void validateStructFieldTypes(StructDecl structDecl) {
        for (StructField field : structDecl.fields()) {
            validateDeclaredType(field.type(), field.range());
            if (directStructName(field.type()).filter(structDecl.name()::equals).isPresent()) {
                report(field.range(), "结构体字段不能直接包含自身：" + structDecl.name());
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
                    report(field.range(), "结构体字段形成递归值包含：" + rootName);
                }
                visiting.remove(currentName);
                return true;
            }
        }
        visiting.remove(currentName);
        return false;
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
        if (!layoutsInProgress.add(structName)) {
            return null;
        }

        try {
            ArrayList<StructFieldLayout> fieldLayouts = new ArrayList<>();
            int offset = 0;
            int structAlignment = 1;
            for (StructField field : structDecl.fields()) {
                int fieldAlignment = alignmentOf(field.type());
                int fieldSize = sizeOf(field.type());
                offset = structDecl.union() ? 0 : alignTo(offset, fieldAlignment);
                fieldLayouts.add(new StructFieldLayout(field.name(), field.type(), offset, fieldSize, fieldAlignment));
                offset = structDecl.union() ? Math.max(offset, fieldSize) : offset + fieldSize;
                if (fieldAlignment > structAlignment) {
                    structAlignment = fieldAlignment;
                }
            }
            int structSize = alignTo(offset == 0 ? 1 : offset, structAlignment);
            StructLayout layout = new StructLayout(structName, structSize, structAlignment, fieldLayouts);
            structLayouts.put(structName, layout);
            return layout;
        } finally {
            layoutsInProgress.remove(structName);
        }
    }

    private int sizeOf(MiniType type) {
        // 非法函数对象已由 validateTypeShape 报错；占位布局只用于让诊断阶段安全完成。
        if (type.isFunction()) {
            return 1;
        }
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
        if (type.isFunction()) {
            return 1;
        }
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

    private void report(SourceRange range, String message) {
        diagnostics.add(new Diagnostic("SEM001", Diagnostic.Severity.ERROR, message, range));
    }
}
