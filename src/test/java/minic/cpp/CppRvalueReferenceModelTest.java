package minic.cpp;

import minic.SourceRange;
import minic.compiler.parser.node.ClassTemplateDecl;
import minic.compiler.parser.node.Declaration.StructField;
import minic.compiler.semantic.cpp.*;
import minic.compiler.type.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.function.UnaryOperator;
import static org.junit.jupiter.api.Assertions.*;

final class CppRvalueReferenceModelTest {
    static final SourceRange RANGE=new SourceRange(1,0,1,1);
    static final MiniType.TemplateParameterType T=new MiniType.TemplateParameterType("forwarding",0);
    static final List<ClassTemplateDecl.Parameter> PARAMETERS=List.of(new ClassTemplateDecl.TypeParameter("T",T,RANGE));
    @Test void collapsingAndSubstitutionPreserveReferenceKinds(){
        assertEquals(MiniType.INT.referenceTo(),MiniType.INT.referenceTo().rvalueReferenceTo());
        assertEquals(MiniType.INT.referenceTo(),MiniType.INT.rvalueReferenceTo().referenceTo());
        assertEquals(MiniType.INT.rvalueReferenceTo(),MiniType.INT.rvalueReferenceTo().rvalueReferenceTo());
        assertEquals(MiniType.INT.referenceTo(),T.rvalueReferenceTo().substituteTemplateParameters(Map.of(T,MiniType.INT.referenceTo())));
        assertEquals(MiniType.INT.rvalueReferenceTo(),T.rvalueReferenceTo().substituteTemplateParameters(Map.of(T,MiniType.INT)));
    }
    @Test void shapeDeductionUsesValueCategoryWithoutPuttingReferencesInExpressionTypes(){
        for(var category:CppValueCategory.values()) {
            var binding=CppFunctionTemplateDeduction.deduceShapes(PARAMETERS,List.of(T.rvalueReferenceTo()),
                    List.of(new CppOverloadResolver.Argument(MiniType.INT,category,false)),List.of(),UnaryOperator.identity(),
                    UnaryOperator.identity(),TemplateValues::evaluate,type->null);
            assertNotNull(binding);
            assertEquals(category==CppValueCategory.LVALUE?MiniType.INT.referenceTo():MiniType.INT,binding.types().get(T));
        }
    }
    @Test void classTemplateParameterIsNotForwardingForMemberConstructor(){
        var binding=CppFunctionTemplateDeduction.deduceShapes(List.of(),List.of(T.rvalueReferenceTo()),
                List.of(new CppOverloadResolver.Argument(MiniType.INT,CppValueCategory.LVALUE,false)),List.of(),
                UnaryOperator.identity(),UnaryOperator.identity(),TemplateValues::evaluate,type->null);
        assertNull(binding);
    }
    @Test void referencesRankByRvalueBindingAndDoNotBindMutableLvalues(){
        var constant=MiniType.qualified(MiniType.INT,Set.of(MiniType.TypeQualifier.CONST));
        var candidates=List.of(new CppOverloadResolver.Candidate<>("lvalue",List.of(MiniType.INT.referenceTo()),false),
                new CppOverloadResolver.Candidate<>("const",List.of(constant.referenceTo()),false),
                new CppOverloadResolver.Candidate<>("rvalue",List.of(MiniType.INT.rvalueReferenceTo()),false));
        for(var category:CppValueCategory.values()) {
            var result=CppOverloadResolver.resolve(candidates,List.of(new CppOverloadResolver.Argument(MiniType.INT,category,false)));
            assertEquals(category==CppValueCategory.LVALUE?"lvalue":"rvalue",result.winner().identity());
        }
        assertFalse(CppOverloadResolver.standardViable(new CppOverloadResolver.Argument(MiniType.INT,CppValueCategory.LVALUE,false),MiniType.INT.rvalueReferenceTo()));
        assertFalse(CppOverloadResolver.standardViable(new CppOverloadResolver.Argument(MiniType.INT,CppValueCategory.XVALUE,false),MiniType.INT.referenceTo()));
    }
    @Test void rvalueReferenceFieldsDeleteCopyButPreserveMoveAlias(){
        MiniType owner=MiniType.struct("Owner");var field=new StructField("alias",MiniType.INT.rvalueReferenceTo(),RANGE);
        var copy=CppCopyConstructorPlan.plan(owner,List.of(field),false,false,type->{throw new AssertionError();});
        assertEquals(CppCopyConstructorPlan.Status.DELETED,copy.status());
        assertEquals(CppCopyConstructorPlan.Failure.RVALUE_REFERENCE_MEMBER,copy.problems().getFirst().reason());
        var move=CppCopyConstructorPlan.planMove(owner,List.of(field),false,false,type->{throw new AssertionError();});
        assertEquals(CppCopyConstructorPlan.Status.AVAILABLE,move.status());
        assertEquals(owner.rvalueReferenceTo(),move.parameterType());
        assertEquals(CppCopyConstructorPlan.Action.REFERENCE,move.entries().getFirst().action());
    }
    @Test void nontrivialMemberMoveOutranksConstCopyAndAccessIsCheckedAfterSelection(){
        MiniType owner=MiniType.struct("Owner"),member=MiniType.struct("Member");
        var constant=MiniType.qualified(member,Set.of(MiniType.TypeQualifier.CONST));
        var result=CppCopyConstructorPlan.planMove(owner,List.of(new StructField("member",member,RANGE)),false,false,
                type->new CppCopyConstructorPlan.Operations<>(List.of(
                        new CppCopyConstructorPlan.Constructor<>("copy",constant.referenceTo(),true,false,false),
                        new CppCopyConstructorPlan.Constructor<>("move",member.rvalueReferenceTo(),false,false,false)),CppCopyConstructorPlan.Destructor.AVAILABLE));
        assertEquals(CppCopyConstructorPlan.Status.DELETED,result.status());
        assertEquals("move",result.problems().getFirst().constructor());
        assertEquals(CppCopyConstructorPlan.Failure.INACCESSIBLE_CONSTRUCTOR,result.problems().getFirst().reason());
    }
}
