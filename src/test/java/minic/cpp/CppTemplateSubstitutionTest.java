package minic.cpp;

import minic.SourceRange;
import minic.compiler.parser.node.*;
import minic.compiler.parser.node.Declaration.*;
import minic.compiler.parser.node.Expression.*;
import minic.compiler.parser.node.Statement.*;
import minic.compiler.semantic.cpp.CppTemplateSubstitution;
import minic.compiler.type.MiniType;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Retained for the final unified run; copying must preserve source identity and call sequencing. */
final class CppTemplateSubstitutionTest {
    private static final SourceRange RANGE=new SourceRange(1,0,1,10);
    @Test void fieldsMetadataRangesAndCallPermutationSurviveSubstitution() {
        var parameter=new MiniType.TemplateParameterType("::Box",0);
        var field=new StructField("value",parameter,RANGE);
        var call=new CallExpr(new NameExpr("combine",RANGE),
                List.of(new NameExpr("value",RANGE),new IntegerLiteralExpr(1,"1",RANGE)),List.of(1,0),RANGE);
        var method=new FunctionDecl("get",parameter,List.of(),false,new BlockStmt(List.of(new ReturnStmt(call,RANGE)),RANGE),false,RANGE);
        var source=new StructDecl("::Box",List.of(field),true,false,
                new CppRecordInfo(RecordKey.STRUCT,List.of(new FieldMember(field),new MethodMember(method,RANGE)),RANGE),RANGE);
        var substitution=new CppTemplateSubstitution(Map.of(parameter,MiniType.DOUBLE),"::Box","::instance");
        StructDecl instance=substitution.instantiate(source);
        assertEquals("::instance",instance.name());assertEquals(MiniType.DOUBLE,instance.fields().getFirst().type());
        assertSame(instance.fields().getFirst(),((FieldMember)instance.cppInfo().members().getFirst()).field());
        var copied=((MethodMember)instance.cppInfo().members().get(1)).method();
        var copiedCall=(CallExpr)((ReturnStmt)copied.body().statements().getFirst()).expression();
        assertEquals(List.of(1,0),copiedCall.argumentEvaluationOrder());
        assertSame(RANGE,copiedCall.range());assertSame(call,substitution.origins().get(copiedCall));
        assertEquals(parameter,source.fields().getFirst().type());
        assertNotSame(source,instance);assertNotSame(call,copiedCall);
    }
}
