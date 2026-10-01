package minic.cpp;

import minic.SourceRange;
import minic.compiler.LanguageMode;
import minic.compiler.ir.IrLowerer;
import minic.compiler.parser.node.*;
import minic.compiler.parser.node.Declaration.*;
import minic.compiler.semantic.SemanticAnalyzer;
import minic.compiler.type.MiniType;
import minic.compiler.type.TypeLayout;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class CppClassTemplateAstTest {
    private static final SourceRange RANGE=new SourceRange(1,0,1,10);

    @Test void typeKeysUseCanonicalOwnerIndexAndStructuralArguments() {
        var t=new MiniType.TemplateParameterType("::N::Box",0);
        assertEquals(t,new MiniType.TemplateParameterType("::N::Box",0));
        assertNotEquals(t,new MiniType.TemplateParameterType("::Other::Box",0));
        assertNotEquals(t,new MiniType.TemplateParameterType("::N::Box",1));
        var arguments=new ArrayList<MiniType>(List.of(MiniType.INT));
        var id=new MiniType.TemplateIdType("::N::Box",arguments);arguments.set(0,MiniType.DOUBLE);
        assertEquals(List.of(new minic.compiler.type.TemplateArgument.Type(MiniType.INT)),id.arguments());
        assertNotEquals(id,new MiniType.TemplateIdType("::N::Box",List.of(MiniType.DOUBLE)));
        assertNotEquals(id,new MiniType.TemplateIdType("::Other::Box",List.of(MiniType.INT)));
        assertThrows(IllegalArgumentException.class,()->new MiniType.TemplateParameterType("::Box",-1));
        var empty = new MiniType.TemplateIdType("::Box", List.<minic.compiler.type.TemplateArgument>of());
        assertTrue(empty.arguments().isEmpty(), "Empty arguments are valid for defaults and empty packs");
        assertEquals("::Box", empty.templateName());
        assertNotEquals(id, empty);
    }

    @Test void sourceTemplateTypesCannotBypassCoreThroughNestedSignaturesOrFlatIndexes() {
        MiniType id=new MiniType.TemplateIdType("::Box",List.of(MiniType.INT));
        for(MiniType type:List.of(id,id.pointerTo(),id.arrayOf(2),MiniType.function(MiniType.INT,List.of(id.pointerTo())).pointerTo())) {
            assertTrue(type.containsTemplateType());
            assertFalse(TypeLayout.hasFixedLayout(type));
            assertThrows(IllegalArgumentException.class,()->TypeLayout.sizeOf(type));
            assertThrows(IllegalArgumentException.class,()->TypeLayout.alignmentOf(type));
            var global=new GlobalVarDecl("value",type,null,false,List.of(),RANGE);
            var program=new Program(List.of(),List.of(),List.of(),List.of(global),List.of(),List.of(),LanguageMode.C,RANGE);
            assertSame(global,AstChildren.firstCppSyntax(program));
            assertNull(AstChildren.firstReferenceSyntax(global));
            var semantic=new SemanticAnalyzer(program);semantic.analyze();
            assertFalse(semantic.succeeded());
            assertTrue(semantic.errors().stream().anyMatch(d->d.code().equals("CPP002")));
            assertThrows(IllegalArgumentException.class,()->new IrLowerer().lower(program));
        }
        var parameter=new Parameter("arg",id.pointerTo(),RANGE);
        var function=new FunctionDecl("fn",MiniType.INT,List.of(parameter),false,null,false,RANGE);
        assertSame(parameter,AstChildren.firstCppSyntax(function));
    }

    @Test void primaryTemplateOwnsSourceRecordAndImmutableParameterList() {
        var parameter=new ClassTemplateDecl.TypeParameter("T",new MiniType.TemplateParameterType("::Box",0),RANGE);
        var record=new StructDecl("::Box",List.of(new StructField("value",parameter.type(),RANGE)),true,false,RANGE);
        var parameters=new ArrayList<>(List.of(parameter));
        var template=new ClassTemplateDecl(parameters,record,RANGE);parameters.clear();
        assertEquals(List.of(parameter),template.parameters());
        assertEquals(List.of(record,parameter),AstChildren.of(template));
        assertSame(template,AstChildren.firstCppSyntax(template));
        assertThrows(IllegalArgumentException.class,()->new ClassTemplateDecl(List.of(new ClassTemplateDecl.TypeParameter("U",new MiniType.TemplateParameterType("::Other",0),RANGE)),record,RANGE));
    }
}
