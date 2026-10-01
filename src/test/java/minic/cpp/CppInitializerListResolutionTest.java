package minic.cpp;

import minic.compiler.semantic.cpp.CppOverloadResolver;
import minic.compiler.semantic.cpp.CppValueCategory;
import minic.compiler.type.MiniType;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static minic.compiler.semantic.cpp.CppOverloadResolver.*;

/** Prepared for the final unified run; the resolver never evaluates initializer elements. */
final class CppInitializerListResolutionTest {
    private static final MiniType INTS=MiniType.struct("std_list_int"), DOUBLES=MiniType.struct("std_list_double");
    private static final UserConversionProvider LISTS=new UserConversionProvider(){
        public UserConversion find(Object candidate,Argument source,MiniType target){return null;}
        public MiniType initializerListElement(MiniType target){
            target=target.isReference()?target.referent():target;
            return target.unqualified().equals(INTS)?MiniType.INT:target.unqualified().equals(DOUBLES)?MiniType.DOUBLE:null;
        }
    };
    private static Argument value(MiniType type){return new Argument(type,CppValueCategory.PRVALUE,false);}
    private static Candidate<String> candidate(String name,MiniType type){return new Candidate<>(name,List.of(type),false);}
    private static Resolution<String> choose(List<Candidate<String>> candidates,Argument argument){
        return resolve(candidates,List.of(argument),null,LISTS);
    }
    @Test void bracedShapesAreUntypedImmutableAndRejectMixedRepresentation(){
        var elements=new ArrayList<Argument>();elements.add(value(MiniType.INT));
        var list=Argument.braced(elements);elements.clear();
        assertNull(list.type());assertTrue(list.braced());assertEquals(1,list.listElements().size());
        assertThrows(UnsupportedOperationException.class,()->list.listElements().clear());
        assertThrows(IllegalArgumentException.class,()->new Argument(MiniType.INT,CppValueCategory.PRVALUE,false,List.of()));
    }
    @Test void initializerListWinsAgainstScalarEvenWhenElementConversionIsWorse(){
        var r=choose(List.of(candidate("scalar",MiniType.INT),candidate("list",DOUBLES)),Argument.braced(List.of(value(MiniType.INT))));
        assertEquals("list",r.winner().identity());
    }
    @Test void elementRankSelectsListAndEmptyListsRemainAmbiguous(){
        var candidates=List.of(candidate("ints",INTS),candidate("doubles",DOUBLES));
        assertEquals("ints",choose(candidates,Argument.braced(List.of(value(MiniType.INT)))).winner().identity());
        assertEquals("doubles",choose(candidates,Argument.braced(List.of(value(MiniType.DOUBLE)))).winner().identity());
        assertEquals(Status.AMBIGUOUS,choose(candidates,Argument.braced(List.of())).status());
    }
    @Test void narrowingIsCheckedAfterSelectionAndDoesNotRemoveViability(){
        assertEquals(Status.SELECTED,choose(List.of(candidate("ints",INTS)),Argument.braced(List.of(value(MiniType.DOUBLE)))).status());
    }
    @Test void referencesPermitSingleExistingListButRejectMutableTemporary(){
        var target=List.of(candidate("ref",INTS.referenceTo()));
        assertEquals(Status.SELECTED,choose(target,Argument.braced(List.of(new Argument(INTS,CppValueCategory.LVALUE,false)))).status());
        assertEquals(Status.NO_VIABLE,choose(target,Argument.braced(List.of(value(MiniType.INT)))).status());
        MiniType readonly=MiniType.qualified(INTS,Set.of(MiniType.TypeQualifier.CONST));
        assertEquals(Status.SELECTED,choose(List.of(candidate("constref",readonly.referenceTo())),Argument.braced(List.of(value(MiniType.INT)))).status());
    }
    @Test void arrayReferenceChecksBoundAndPreservesStaticReceiverNeutrality(){
        MiniType element=MiniType.qualified(MiniType.INT,Set.of(MiniType.TypeQualifier.CONST));
        var list=Argument.braced(List.of(value(MiniType.INT),value(MiniType.INT)));
        assertEquals(Status.NO_VIABLE,choose(List.of(candidate("small",element.arrayOf(1).referenceTo())),list).status());
        assertEquals(Status.SELECTED,choose(List.of(candidate("fits",element.arrayOf(3).referenceTo())),list).status());
        var staticMethod=new Candidate<>("static",List.of(INTS),false,null,true);
        assertEquals(Status.SELECTED,resolve(List.of(staticMethod),List.of(list),
                new Argument(MiniType.struct("Owner"),CppValueCategory.LVALUE,false),LISTS).status());
    }
}
