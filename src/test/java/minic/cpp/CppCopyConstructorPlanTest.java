package minic.cpp;

import minic.SourceRange;
import minic.compiler.parser.node.Declaration.StructField;
import minic.compiler.semantic.cpp.CppCopyConstructorPlan;
import minic.compiler.semantic.cpp.CppCopyConstructorPlan.*;
import minic.compiler.type.MiniType;
import minic.cpp.support.BoundedProcess;
import minic.cpp.support.CppDifferentialHarness;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import static minic.compiler.type.MiniType.TypeQualifier.*;
import static org.junit.jupiter.api.Assertions.*;

final class CppCopyConstructorPlanTest {
    @TempDir Path temporary;
    static final MiniType OWNER=MiniType.struct("::Outer"), MEMBER=MiniType.struct("::Member");
    static MiniType cv(MiniType type,MiniType.TypeQualifier...qualifiers){return MiniType.qualified(type,Set.of(qualifiers));}
    static StructField field(String name,MiniType type){return new StructField(name,type,new SourceRange(2,0,2,9));}
    static Constructor<String> ctor(String name,MiniType referent,boolean accessible,boolean deleted,boolean trivial){return new Constructor<>(name,referent.referenceTo(),accessible,deleted,trivial);}
    @SafeVarargs static Operations<String> ops(Constructor<String>...constructors){return new Operations<>(List.of(constructors),Destructor.AVAILABLE);}
    static Result<String> plan(List<StructField> fields,Operations<String> member){return CppCopyConstructorPlan.plan(OWNER,fields,false,false,type->{assertEquals(MEMBER,type);return member;});}
    static Stream<Arguments> signatures(){return Stream.of(
            Arguments.of(List.of(),Classification.ORDINARY),
            Arguments.of(List.of(MiniType.INT),Classification.ORDINARY),
            Arguments.of(List.of(OWNER),Classification.INVALID_BY_VALUE),
            Arguments.of(List.of(cv(OWNER,CONST)),Classification.INVALID_BY_VALUE),
            Arguments.of(List.of(OWNER.referenceTo()),Classification.COPY),
            Arguments.of(List.of(cv(OWNER,CONST).referenceTo()),Classification.COPY),
            Arguments.of(List.of(cv(OWNER,VOLATILE).referenceTo()),Classification.COPY),
            Arguments.of(List.of(cv(OWNER,CONST,VOLATILE).referenceTo()),Classification.COPY),
            Arguments.of(List.of(MEMBER.referenceTo()),Classification.ORDINARY),
            Arguments.of(List.of(OWNER.pointerTo()),Classification.ORDINARY),
            Arguments.of(List.of(OWNER.referenceTo(),MiniType.INT),Classification.ORDINARY));}
    @ParameterizedTest @MethodSource("signatures") void sourceSignaturesAreClassifiedBeforePointerAbi(List<MiniType> parameters,Classification expected){assertEquals(expected,CppCopyConstructorPlan.classify(OWNER,parameters));}

    @Test void scalarConstPointerAndReferenceMembersKeepOrderAndIdentity(){
        var scalar=field("number",MiniType.INT);var pointer=field("pointer",MiniType.INT.pointerTo());
        var reference=field("alias",MEMBER.referenceTo());var constant=field("constant",cv(MiniType.INT,CONST));
        var result=plan(List.of(scalar,pointer,reference,constant),null);
        assertEquals(Status.AVAILABLE,result.status());assertEquals(cv(OWNER,CONST).referenceTo(),result.parameterType());assertTrue(result.trivial());
        assertEquals(List.of(scalar,pointer,reference,constant),result.entries().stream().map(Entry::field).toList());assertSame(reference,result.entries().get(2).field());
        assertEquals(Action.REFERENCE,result.entries().get(2).action());assertEquals(MEMBER,result.entries().get(2).sourceType());
        assertEquals(cv(MiniType.INT.pointerTo(),CONST),result.entries().get(1).sourceType());
        assertThrows(UnsupportedOperationException.class,()->result.entries().clear());
    }
    @Test void mutableMemberCopyChangesTheImplicitSignature(){
        var result=plan(List.of(field("member",MEMBER)),ops(ctor("mutable",MEMBER,true,false,false)));
        assertEquals(OWNER.referenceTo(),result.parameterType());assertEquals(Status.AVAILABLE,result.status());
        assertEquals("mutable",result.entries().getFirst().constructor());assertFalse(result.trivial());
    }
    @Test void declaredConstCopyControlsSignatureEvenWhenItIsDeletedOrPrivate(){
        for(boolean inaccessible:List.of(false,true)){
            var result=plan(List.of(field("member",MEMBER)),ops(ctor("mutable",MEMBER,true,false,false),ctor("const",cv(MEMBER,CONST),!inaccessible,!inaccessible,false)));
            assertEquals(cv(OWNER,CONST).referenceTo(),result.parameterType());assertEquals(Status.DELETED,result.status());assertTrue(result.entries().isEmpty());
            assertEquals(inaccessible?Failure.INACCESSIBLE_CONSTRUCTOR:Failure.DELETED_CONSTRUCTOR,result.problems().getFirst().reason());
            assertEquals("const",result.problems().getFirst().constructor());
        }
    }
    @Test void betterDeletedCandidateIsNotBypassedForWorseAccessibleOverload(){
        var result=plan(List.of(field("member",MEMBER)),ops(ctor("const",cv(MEMBER,CONST),true,true,false),ctor("cv",cv(MEMBER,CONST,VOLATILE),true,false,false)));
        assertEquals(Status.DELETED,result.status());assertEquals("const",result.problems().getFirst().constructor());
    }
    @Test void ambiguousCopyMembersDeleteRatherThanArbitrarilyChoosing(){
        var result=plan(List.of(field("member",MEMBER)),ops(ctor("one",cv(MEMBER,CONST),true,false,false),ctor("two",cv(MEMBER,CONST),true,false,false)));
        assertEquals(Failure.AMBIGUOUS_CONSTRUCTOR,result.problems().getFirst().reason());
    }
    @Test void constMemberCannotBindToMutableOnlyCopy(){
        var result=plan(List.of(field("member",cv(MEMBER,CONST))),ops(ctor("mutable",MEMBER,true,false,false)));
        assertEquals(OWNER.referenceTo(),result.parameterType());assertEquals(Status.DELETED,result.status());assertEquals(Failure.NO_VIABLE_CONSTRUCTOR,result.problems().getFirst().reason());
    }
    @Test void volatileRecordMemberRequiresVolatileCompatibleCopy(){
        var no=plan(List.of(field("member",cv(MEMBER,VOLATILE))),ops(ctor("const",cv(MEMBER,CONST),true,false,false)));
        assertEquals(Failure.NO_VIABLE_CONSTRUCTOR,no.problems().getFirst().reason());
        var yes=plan(List.of(field("member",cv(MEMBER,VOLATILE))),ops(ctor("cv",cv(MEMBER,CONST,VOLATILE),true,false,false)));
        assertEquals(Status.AVAILABLE,yes.status());assertEquals(cv(MEMBER,CONST,VOLATILE),yes.entries().getFirst().sourceType());
    }
    @Test void nestedArraysSelectOnceAndRetainElementDimensionsAndCv(){
        var member=field("elements",cv(MEMBER.arrayOf(3).arrayOf(2),VOLATILE));
        var result=plan(List.of(member),ops(ctor("cv",cv(MEMBER,CONST,VOLATILE),true,false,false)));
        var entry=result.entries().getFirst();assertSame(member,entry.field());assertEquals(List.of(2,3),entry.arrayDimensions());
        assertEquals(cv(MEMBER,CONST,VOLATILE),entry.sourceType());assertEquals(Action.CONSTRUCTOR,entry.action());assertEquals("cv",entry.constructor());
        assertThrows(UnsupportedOperationException.class,()->entry.arrayDimensions().clear());
    }
    @Test void scalarArraysUseElementwiseValueCopyWithoutClassLookup(){
        var result=plan(List.of(field("values",MiniType.INT.arrayOf(4).arrayOf(2))),null);
        assertTrue(result.trivial());assertEquals(Action.VALUE,result.entries().getFirst().action());assertEquals(List.of(2,4),result.entries().getFirst().arrayDimensions());
    }
    @Test void memberDestructorAccessibilityAlsoDeletesTheImplicitCopy(){
        for(var availability:List.of(Destructor.DELETED,Destructor.INACCESSIBLE)){
            var result=plan(List.of(field("member",MEMBER)),new Operations<>(List.of(ctor("copy",cv(MEMBER,CONST),true,false,true)),availability));
            assertEquals(Status.DELETED,result.status());assertEquals(availability==Destructor.DELETED?Failure.DELETED_DESTRUCTOR:Failure.INACCESSIBLE_DESTRUCTOR,result.problems().getFirst().reason());
            assertEquals("copy",result.problems().getFirst().constructor());
        }
    }
    @Test void userDeclaredCopySuppressesImplicitSynthesisWithoutVisitingMembers(){
        var result=CppCopyConstructorPlan.<String>plan(OWNER,List.of(field("member",MEMBER)),false,true,type->{throw new AssertionError("User copy must not silently get an implicit fallback");});
        assertEquals(Status.SUPPRESSED,result.status());assertNull(result.parameterType());assertTrue(result.entries().isEmpty());assertFalse(result.trivial());
    }
    @Test void unionUsesRepresentationCopyOnlyWithTrivialSelectedMemberCopies(){
        var good=CppCopyConstructorPlan.plan(OWNER,List.of(field("member",MEMBER)),true,false,type->ops(ctor("copy",cv(MEMBER,CONST),true,false,true)));
        assertEquals(Status.AVAILABLE,good.status());assertTrue(good.objectRepresentation());assertTrue(good.entries().isEmpty());assertTrue(good.trivial());
        var bad=CppCopyConstructorPlan.plan(OWNER,List.of(field("member",MEMBER)),true,false,type->ops(ctor("copy",cv(MEMBER,CONST),true,false,false)));
        assertEquals(Status.DELETED,bad.status());assertEquals(Failure.NONTRIVIAL_UNION_MEMBER,bad.problems().getFirst().reason());assertFalse(bad.objectRepresentation());
    }
    @Test void missingMemberInformationCannotPretendToBeTrivialCopy(){
        assertThrows(IllegalArgumentException.class,()->plan(List.of(field("member",MEMBER)),null));
    }
    @Test void functionReferencesRemainAliasesWithoutClassLookup(){
        MiniType function=MiniType.function(MiniType.INT,List.of(MiniType.INT));
        var result=plan(List.of(field("callback",function.referenceTo())),null);
        assertEquals(Action.REFERENCE,result.entries().getFirst().action());assertEquals(function,result.entries().getFirst().sourceType());
    }
    @Test void operationSnapshotsMustReferToTheCanonicalMemberIdentity(){
        assertThrows(IllegalArgumentException.class,()->plan(List.of(field("member",MEMBER)),ops(ctor("wrong",cv(OWNER,CONST),true,false,true))));
    }
    @Test void snapshotsCannotBeMutated(){
        var data=ops(ctor("copy",cv(MEMBER,CONST),true,false,true));assertThrows(UnsupportedOperationException.class,()->data.constructors().clear());
        var result=plan(List.of(field("member",MEMBER)),new Operations<>(data.constructors(),Destructor.DELETED));assertThrows(UnsupportedOperationException.class,()->result.problems().clear());
    }

    static Stream<Arguments> cppOracles(){return Stream.of(
            Arguments.of("mutable-member","struct M{M(M&);};struct O{M value;};",true,false),
            Arguments.of("const-member","struct M{M(const M&);};struct O{M value;};",true,true),
            Arguments.of("private-const-no-mutable-fallback","struct M{M(M&);private:M(const M&);};struct O{M value;};",false,false),
            Arguments.of("deleted-const-no-mutable-fallback","struct M{M(M&);M(const M&)=delete;};struct O{M value;};",false,false),
            Arguments.of("const-member-with-mutable-copy","struct M{M(M&);};struct O{const M value;};",false,false),
            Arguments.of("volatile-member-incompatible","struct M{M(const M&);};struct O{volatile M value;};",false,false),
            Arguments.of("volatile-member-compatible","struct M{M(const volatile M&);};struct O{volatile M value;};",true,true),
            Arguments.of("member-array","struct M{M(M&);};struct O{M value[2][3];};",true,false),
            Arguments.of("reference-const-fields","struct M{M(const M&)=delete;};struct O{M&value;const int tag;};",true,true),
            Arguments.of("private-member-destructor","struct M{M(const M&);private:~M();};struct O{M value;};",false,false),
            Arguments.of("union-nontrivial-copy","struct M{M(const M&){}};union O{M value;int n;};",false,false),
            Arguments.of("user-copy-no-implicit-const-fallback","struct O{O(O&);};",true,false));}
    @ParameterizedTest(name="{0}") @MethodSource("cppOracles")
    void standardCompilerConfirmsImplicitCopyViability(String name,String declarations,boolean mutable,boolean constant)throws Exception{
        String source="#include <type_traits>\n"+declarations+"\nstatic_assert(std::is_constructible<O,O&>::value=="+mutable+");static_assert(std::is_constructible<O,const O&>::value=="+constant+");";
        Path file=temporary.resolve(name+".cpp");Files.writeString(file,source);
        var result=BoundedProcess.run(List.of(CppDifferentialHarness.referenceCompiler(System.getenv()),"-std=c++17","-pedantic-errors","-fsyntax-only",file.toString()),temporary,"",Duration.ofSeconds(20),65536);
        assertFalse(result.timedOut());assertEquals(0,result.exitCode(),result::stderr);
    }
}
