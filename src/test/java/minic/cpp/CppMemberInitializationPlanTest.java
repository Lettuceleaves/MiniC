package minic.cpp;

import minic.SourceRange;
import minic.compiler.parser.node.CppInitializer;
import minic.compiler.parser.node.Declaration.*;
import minic.compiler.parser.node.Expression.IntegerLiteralExpr;
import minic.compiler.parser.node.QualifiedName;
import minic.compiler.parser.node.Statement.BlockStmt;
import minic.compiler.semantic.cpp.CppMemberInitializationPlan;
import minic.compiler.semantic.cpp.CppMemberInitializationPlan.Origin;
import minic.compiler.type.MiniType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CppMemberInitializationPlanTest {
    private static final SourceRange RECORD=new SourceRange(1,0,8,2);
    private static final SourceRange FIRST=new SourceRange(2,4,2,14);
    private static final SourceRange SECOND=new SourceRange(3,4,3,15);
    private static final SourceRange CTOR=new SourceRange(5,4,7,5);
    private static final SourceRange INIT_ONE=new SourceRange(5,12,5,20);
    private static final SourceRange INIT_TWO=new SourceRange(6,12,6,20);

    @Test void declarationOrderOverridesWrittenMemberOrderWithoutCopyingSourceNodes() {
        var first=field("first",FIRST);
        var second=field("second",SECOND);
        var secondInit=member("second",2,INIT_ONE);
        var firstInit=member("first",1,INIT_TWO);
        var result=CppMemberInitializationPlan.plan(record(List.of(first,second),false),ctor(List.of(secondInit,firstInit)));
        assertTrue(result.diagnostics().isEmpty());
        assertEquals(List.of(first.field(),second.field()),result.entries().stream().map(CppMemberInitializationPlan.Entry::field).toList());
        assertSame(first.field(),result.entries().getFirst().field());
        assertSame(firstInit.initializer(),result.entries().getFirst().initializer());
        assertSame(firstInit,result.entries().getFirst().source());
        assertSame(secondInit.initializer(),result.entries().getLast().initializer());
        assertEquals(Origin.EXPLICIT,result.entries().getFirst().origin());
        assertThrows(UnsupportedOperationException.class,()->result.entries().clear());
        assertThrows(UnsupportedOperationException.class,()->result.diagnostics().clear());
    }

    @Test void explicitInitializerSuppressesDmiAndMissingInitializerRemainsDefault() {
        var first=new FieldMember(field("first",FIRST).field(),initializer(1,FIRST));
        var second=new FieldMember(field("second",SECOND).field(),initializer(2,SECOND));
        var third=field("third",new SourceRange(4,4,4,14));
        var explicit=member("second",9,INIT_ONE);
        var result=CppMemberInitializationPlan.plan(record(List.of(first,second,third),false),ctor(List.of(explicit)));
        assertTrue(result.diagnostics().isEmpty());
        assertEquals(List.of(Origin.DEFAULT_MEMBER,Origin.EXPLICIT,Origin.DEFAULT),
                result.entries().stream().map(CppMemberInitializationPlan.Entry::origin).toList());
        assertSame(first.defaultInitializer(),result.entries().get(0).initializer());
        assertSame(first,result.entries().get(0).source());
        assertSame(explicit.initializer(),result.entries().get(1).initializer());
        assertEquals(CppInitializer.Kind.DEFAULT,result.entries().get(2).initializer().kind());
        assertTrue(result.entries().get(2).initializer().arguments().isEmpty());
        assertSame(third.field(),result.entries().get(2).source());
        assertEquals(third.range(),result.entries().get(2).initializer().range());
    }

    @Test void legacyFieldProjectionWorksWithoutCppRecordMetadata() {
        var field=field("first",FIRST).field();
        var result=CppMemberInitializationPlan.plan(new StructDecl("Box",List.of(field),RECORD),ctor(List.of()));
        assertTrue(result.diagnostics().isEmpty());
        assertSame(field,result.entries().getFirst().field());
        assertEquals(Origin.DEFAULT,result.entries().getFirst().origin());
    }

    @Test void duplicateInitializerReportsTheSecondSourceOccurrenceAndReturnsNoPartialPlan() {
        var result=CppMemberInitializationPlan.plan(record(List.of(field("first",FIRST)),false),
                ctor(List.of(member("first",1,INIT_ONE),member("first",2,INIT_TWO))));
        assertFailure(result,"CPP004",INIT_TWO);
    }

    @Test void unknownMemberReportsItsOwnTargetRange() {
        var result=CppMemberInitializationPlan.plan(record(List.of(field("first",FIRST)),false),
                ctor(List.of(member("absent",1,INIT_ONE))));
        assertFailure(result,"CPP004",INIT_ONE);
    }

    @Test void delegationIsAnExplicitUnsupportedBoundary() {
        var result=CppMemberInitializationPlan.plan(record(List.of(field("first",FIRST)),false),
                ctor(List.of(member("Box",1,INIT_ONE))));
        assertFailure(result,"CPP005",INIT_ONE);
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void qualifiedTargetsAreNotMistakenForDirectMembers(boolean global) {
        var target=new QualifiedName(global,global?List.of("first"):List.of("Base","first"),INIT_ONE);
        var member=new MemberInitializer(target,initializer(1,INIT_ONE),INIT_ONE);
        var result=CppMemberInitializationPlan.plan(record(List.of(field("first",FIRST)),false),ctor(List.of(member)));
        assertFailure(result,"CPP005",INIT_ONE);
    }

    @Test void unionsAndPromotedAnonymousFieldsDoNotReceiveInventedConstructionOrder() {
        var union=CppMemberInitializationPlan.plan(record(List.of(field("first",FIRST)),true),ctor(List.of()));
        assertFailure(union,"CPP005",RECORD);
        var anonymous=new StructField("",MiniType.struct("Anonymous"),true,List.of(),FIRST);
        var result=CppMemberInitializationPlan.plan(record(List.of(new FieldMember(anonymous)),false),ctor(List.of()));
        assertFailure(result,"CPP005",FIRST);
    }

    @Test void prototypeAndForwardRecordAreNotExecutableInitializationPlans() {
        var prototype=new ConstructorMember("Box",List.of(),false,List.of(),null,CTOR,CTOR);
        assertFailure(CppMemberInitializationPlan.plan(record(List.of(),false),prototype),"CPP004",CTOR);
        assertFailure(CppMemberInitializationPlan.plan(new StructDecl("Box",List.of(),false,RECORD),ctor(List.of())),"CPP004",RECORD);
    }

    private static void assertFailure(CppMemberInitializationPlan.Result result,String code,SourceRange range) {
        assertTrue(result.entries().isEmpty(),"An invalid partial plan must never be executable");
        assertTrue(result.diagnostics().stream().anyMatch(d->d.code().equals(code)&&d.range().equals(range)),
                ()->result.diagnostics().toString());
    }
    private static FieldMember field(String name,SourceRange range){return new FieldMember(new StructField(name,MiniType.INT,range));}
    private static CppInitializer initializer(int value,SourceRange range){
        return new CppInitializer(CppInitializer.Kind.DIRECT_PAREN,List.of(new IntegerLiteralExpr(value,Integer.toString(value),range)),range);
    }
    private static MemberInitializer member(String name,int value,SourceRange range){
        return new MemberInitializer(new QualifiedName(false,List.of(name),range),initializer(value,range),range);
    }
    private static ConstructorMember ctor(List<MemberInitializer> initializers){
        return new ConstructorMember("Box",List.of(),false,initializers,new BlockStmt(List.of(),CTOR),CTOR,CTOR);
    }
    private static StructDecl record(List<FieldMember> fields,boolean union){
        return new StructDecl("::N::Box",fields.stream().map(FieldMember::field).toList(),true,union,
                new CppRecordInfo(RecordKey.STRUCT,List.copyOf(fields),RECORD),RECORD);
    }
}
