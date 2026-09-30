package minic.stdlib;

import minic.compiler.SourceFile;
import minic.compiler.lexer.Lexer;
import minic.compiler.parser.Parser;
import minic.compiler.preprocess.PreprocessResult;
import minic.compiler.preprocess.Preprocessor;
import minic.compiler.type.MiniType;
import minic.compiler.type.TypeLayout;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("stdlib-contract")
final class CoreModelHeadersContractTest {
    @Test
    void limitsHeaderFreezesTheSupportedWindowsLlp64IntegerModel() {
        Map<String, String> macros = macrosFrom("limits.mh");
        Map<String, String> expected = Map.ofEntries(
                Map.entry("BOOL_WIDTH", "1"),
                Map.entry("BOOL_MAX", "1"),
                Map.entry("CHAR_BIT", "8"),
                Map.entry("CHAR_WIDTH", "8"),
                Map.entry("SCHAR_WIDTH", "8"),
                Map.entry("UCHAR_WIDTH", "8"),
                Map.entry("SHRT_WIDTH", "16"),
                Map.entry("USHRT_WIDTH", "16"),
                Map.entry("INT_WIDTH", "32"),
                Map.entry("UINT_WIDTH", "32"),
                Map.entry("LONG_WIDTH", "32"),
                Map.entry("ULONG_WIDTH", "32"),
                Map.entry("LLONG_WIDTH", "64"),
                Map.entry("ULLONG_WIDTH", "64"),
                Map.entry("MB_LEN_MAX", "1"),
                Map.entry("SCHAR_MIN", "(-127 - 1)"),
                Map.entry("SCHAR_MAX", "127"),
                Map.entry("UCHAR_MAX", "255U"),
                Map.entry("CHAR_MIN", "SCHAR_MIN"),
                Map.entry("CHAR_MAX", "SCHAR_MAX"),
                Map.entry("SHRT_MIN", "(-32767 - 1)"),
                Map.entry("SHRT_MAX", "32767"),
                Map.entry("USHRT_MAX", "65535U"),
                Map.entry("INT_MIN", "(-2147483647 - 1)"),
                Map.entry("INT_MAX", "2147483647"),
                Map.entry("UINT_MAX", "4294967295U"),
                Map.entry("LONG_MIN", "(-2147483647L - 1L)"),
                Map.entry("LONG_MAX", "2147483647L"),
                Map.entry("ULONG_MAX", "4294967295UL"),
                Map.entry("LLONG_MIN", "(-9223372036854775807LL - 1LL)"),
                Map.entry("LLONG_MAX", "9223372036854775807LL"),
                Map.entry("ULLONG_MAX", "18446744073709551615ULL")
        );

        expected.forEach((name, replacement) -> assertEquals(replacement, macros.get(name), name));
        assertFalse(macros.containsKey("BITINT_MAXWIDTH"), "_BitInt is not implemented");
        assertFalse(macros.containsKey("__STDC_VERSION_LIMITS_H__"),
                "a partial limits.mh must not claim the complete C23 contract");
    }

    @Test
    void floatHeaderFreezesBinary32AndBinary64WithLongDoubleMappedToDouble() {
        Map<String, String> macros = macrosFrom("float.mh");
        Map<String, String> expected = Map.ofEntries(
                Map.entry("FLT_RADIX", "2"),
                Map.entry("FLT_ROUNDS", "1"),
                Map.entry("FLT_EVAL_METHOD", "0"),
                Map.entry("FLT_IS_IEC_60559", "1"),
                Map.entry("DBL_IS_IEC_60559", "1"),
                Map.entry("LDBL_IS_IEC_60559", "DBL_IS_IEC_60559"),
                Map.entry("FLT_MANT_DIG", "24"),
                Map.entry("DBL_MANT_DIG", "53"),
                Map.entry("LDBL_MANT_DIG", "DBL_MANT_DIG"),
                Map.entry("FLT_NORM_MAX", "FLT_MAX"),
                Map.entry("DBL_NORM_MAX", "DBL_MAX"),
                Map.entry("LDBL_NORM_MAX", "DBL_NORM_MAX"),
                Map.entry("LDBL_DECIMAL_DIG", "DBL_DECIMAL_DIG"),
                Map.entry("LDBL_DIG", "DBL_DIG"),
                Map.entry("LDBL_MIN_EXP", "DBL_MIN_EXP"),
                Map.entry("LDBL_MIN_10_EXP", "DBL_MIN_10_EXP"),
                Map.entry("LDBL_MAX_EXP", "DBL_MAX_EXP"),
                Map.entry("LDBL_MAX_10_EXP", "DBL_MAX_10_EXP"),
                Map.entry("LDBL_MAX", "DBL_MAX"),
                Map.entry("LDBL_EPSILON", "DBL_EPSILON"),
                Map.entry("LDBL_MIN", "DBL_MIN"),
                Map.entry("LDBL_TRUE_MIN", "DBL_TRUE_MIN"),
                Map.entry("LDBL_HAS_SUBNORM", "DBL_HAS_SUBNORM")
        );

        expected.forEach((name, replacement) -> assertEquals(replacement, macros.get(name), name));
        assertFalse(macros.containsKey("FLT_SNAN"), "signaling-NaN constants are not implemented");
        assertFalse(macros.containsKey("DBL_SNAN"), "signaling-NaN constants are not implemented");
        assertFalse(macros.containsKey("LDBL_SNAN"), "signaling-NaN constants are not implemented");
        assertFalse(macros.containsKey("__STDC_VERSION_FLOAT_H__"),
                "a partial float.mh must not claim the complete C23 contract");
    }

    @Test
    void stddefHeaderExposesTheRealPointerSizedTypesAlignmentNullAndOffsetof() {
        PreprocessResult preprocessed = preprocess("stddef.mh");
        Map<String, String> macros = definedMacros(preprocessed);
        assertEquals("((void *)0)", macros.get("NULL"));
        assertEquals("((size_t)&(((type *)0)->member))", macros.get("offsetof"));

        Lexer lexer = new Lexer(preprocessed.sourceFile());
        var lexed = lexer.lex();
        assertTrue(lexer.errors().isEmpty(), lexer.errors()::toString);
        var parsedStage = new Parser(lexed.tokens());
        var parsed = parsedStage.parse();
        assertTrue(parsedStage.errors().isEmpty(), parsedStage.errors()::toString);

        Map<String, MiniType> typedefs = new LinkedHashMap<>();
        parsed.program().typedefs().forEach(declaration -> typedefs.put(declaration.name(), declaration.type()));
        assertEquals(MiniType.UNSIGNED_LONG_LONG, typedefs.get("size_t"));
        assertEquals(MiniType.LONG_LONG, typedefs.get("ptrdiff_t"));
        assertEquals(MiniType.UNSIGNED_SHORT, typedefs.get("wchar_t"));
        assertEquals(MiniType.DOUBLE, typedefs.get("max_align_t"));
        assertEquals(8, TypeLayout.sizeOf(typedefs.get("size_t")));
        assertEquals(8, TypeLayout.sizeOf(typedefs.get("ptrdiff_t")));
        assertEquals(2, TypeLayout.sizeOf(typedefs.get("wchar_t")));
        assertEquals(8, TypeLayout.alignmentOf(typedefs.get("max_align_t")));

        assertFalse(typedefs.containsKey("nullptr_t"), "nullptr is not implemented");
        assertFalse(macros.containsKey("unreachable"), "unreachable semantics are not implemented");
        assertFalse(macros.containsKey("__STDC_VERSION_STDDEF_H__"),
                "a partial stddef.mh must not claim the complete C23 contract");
    }

    private static Map<String, String> macrosFrom(String header) {
        return definedMacros(preprocess(header));
    }

    private static PreprocessResult preprocess(String header) {
        var resultStage = new Preprocessor();
        PreprocessResult result = resultStage.preprocess(
                new SourceFile("contract-" + header + ".mc", "#include \"" + header + "\"\n"));
        assertTrue(resultStage.errors().isEmpty(), resultStage.errors()::toString);
        return result;
    }

    private static Map<String, String> definedMacros(PreprocessResult result) {
        LinkedHashMap<String, String> macros = new LinkedHashMap<>();
        result.macros().forEach(summary -> {
            if (summary.defined()) {
                macros.put(summary.name(), summary.replacement());
            } else {
                macros.remove(summary.name());
            }
        });
        return macros;
    }
}
