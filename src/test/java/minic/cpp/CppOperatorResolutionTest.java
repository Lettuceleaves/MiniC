package minic.cpp;

import minic.compiler.semantic.cpp.CppOverloadResolver;
import minic.compiler.semantic.cpp.CppValueCategory;
import minic.compiler.type.MiniType;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

final class CppOperatorResolutionTest {
    private static final MiniType TYPE=MiniType.struct("Value");
    private static final MiniType CONST=MiniType.qualified(TYPE, Set.of(MiniType.TypeQualifier.CONST));
    private static CppOverloadResolver.Argument value(MiniType type,CppValueCategory category){
        return new CppOverloadResolver.Argument(type,category,false);
    }
    @Test void freeAndMemberCandidatesCompareTheSameOperandPositions(){
        var member=new CppOverloadResolver.Candidate<>("member",List.of(MiniType.LONG_LONG),false,CONST);
        var free=new CppOverloadResolver.Candidate<>("free",List.of(CONST.referenceTo(),MiniType.INT),false);
        var operands=List.of(value(TYPE,CppValueCategory.LVALUE),value(MiniType.INT,CppValueCategory.PRVALUE));
        var resolution=CppOverloadResolver.resolveOperators(List.of(member,free),operands);
        assertEquals("free",resolution.winner().identity());
    }
    @Test void crossedOperandRanksRemainAmbiguous(){
        var member=new CppOverloadResolver.Candidate<>("member",List.of(MiniType.LONG_LONG),false,TYPE);
        var free=new CppOverloadResolver.Candidate<>("free",List.of(CONST.referenceTo(),MiniType.INT),false);
        var resolution=CppOverloadResolver.resolveOperators(List.of(member,free),
                List.of(value(TYPE,CppValueCategory.LVALUE),value(MiniType.INT,CppValueCategory.PRVALUE)));
        assertEquals(CppOverloadResolver.Status.AMBIGUOUS,resolution.status());
    }
    @Test void prvalueCanBindUnqualifiedMemberReceiverButCannotBindMutableFreeReference(){
        var member=new CppOverloadResolver.Candidate<>("member",List.<MiniType>of(),false,TYPE);
        var free=new CppOverloadResolver.Candidate<>("free",List.of(TYPE.referenceTo()),false);
        var result=CppOverloadResolver.resolveOperators(List.of(member,free),List.of(value(TYPE,CppValueCategory.PRVALUE)));
        assertEquals("member",result.winner().identity());
        assertEquals(List.of(member),result.viable());
    }
    @Test void constReceiverAndArityRulesStillApply(){
        var member=new CppOverloadResolver.Candidate<>("member",List.<MiniType>of(),false,TYPE);
        assertEquals(CppOverloadResolver.Status.NO_VIABLE,CppOverloadResolver.resolveOperators(List.of(member),List.of()).status());
        assertEquals(CppOverloadResolver.Status.NO_VIABLE,CppOverloadResolver.resolveOperators(List.of(member),List.of(value(CONST,CppValueCategory.LVALUE))).status());
        assertEquals(CppOverloadResolver.Status.NO_VIABLE,CppOverloadResolver.resolve(List.of(member),List.of()).status());
    }
}

