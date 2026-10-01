package minic.cpp;

import minic.SourceRange;
import minic.compiler.ir.manager.GlobalDataLowerer;
import minic.compiler.ir.manager.StringLiteralRegistry;
import minic.compiler.ir.model.IrGlobalData;
import minic.compiler.parser.node.Declaration.GlobalVarDecl;
import minic.compiler.parser.node.Expression;
import minic.compiler.parser.node.Expression.*;
import minic.compiler.lexer.token.TokenType;
import minic.compiler.type.MiniType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(120) final class CppStaticFunctionAddressTest {
 @TempDir Path temporary;
 private static final SourceRange RANGE=new SourceRange(1,0,1,1);
 private static final MiniType FUNCTION=MiniType.function(MiniType.INT,List.of(MiniType.INT),false);
 @Test void decayedFunctionExpressionStillRelocatesToFunctionSymbol(){
  var name=new NameExpr("target",RANGE);
  var result=lower(name,Map.of(name,FUNCTION.pointerTo()),Map.of("target",FUNCTION));
  assertEquals(List.of(new IrGlobalData.Address(0,"target",0,IrGlobalData.AddressKind.FUNCTION)),result.addresses());
 }
 @Test void explicitAddressUsesDeclarationIdentityEvenWhenDesignatorHasDecayedType(){
  var name=new NameExpr("target",RANGE);
  var result=lower(new UnaryExpr(TokenType.AMPERSAND,name,RANGE),Map.of(name,FUNCTION.pointerTo()),Map.of("target",FUNCTION));
  assertEquals(List.of(new IrGlobalData.Address(0,"target",0,IrGlobalData.AddressKind.FUNCTION)),result.addresses());
 }
 @Test void pointerObjectAddressRemainsObjectRelocation(){
  var name=new NameExpr("slot",RANGE);
  var result=lower(new UnaryExpr(TokenType.AMPERSAND,name,RANGE),Map.of(name,FUNCTION.pointerTo()),Map.of("slot",FUNCTION.pointerTo()));
  assertEquals(List.of(new IrGlobalData.Address(0,"slot",0,IrGlobalData.AddressKind.OBJECT)),result.addresses());
 }
 private static IrGlobalData lower(Expression initializer,Map<Expression,MiniType> types,Map<String,MiniType> symbols){
  var global=new GlobalVarDecl("pointer",FUNCTION.pointerTo(),initializer,false,List.of(),RANGE);
  return new GlobalDataLowerer(Map.of(),types,new StringLiteralRegistry(),symbols).lower(List.of(global)).getFirst();
 }
 static Stream<Arguments> programs(){return Stream.of(
  Arguments.of("ordinary-function","int twice(int n){return n*2;}int(*pointer)(int)=twice;int main(){printf(\"%d %d\\n\",pointer(7),pointer==&twice);return 0;}","14 1\n"),
  Arguments.of("constexpr-function","constexpr int plus(int n){return n+3;}constexpr int(*pointer)(int)=plus;static_assert(pointer(4)==7);int main(){printf(\"%d %d\\n\",pointer(8),pointer==plus);return 0;}","11 1\n"),
  Arguments.of("lambda-thunk","constexpr int(*pointer)(int)=[](int n){return n+1;};static_assert(pointer(4)==5);int main(){printf(\"%d\\n\",pointer(8));return 0;}","9\n"),
  Arguments.of("dereferenced-function","constexpr int plus(int n){return n+1;}constexpr auto pointer=&plus;static_assert((*pointer)(3)==4);static_assert(&*pointer==pointer);int main(){printf(\"%d %d\\n\",(*pointer)(6),&*pointer==pointer);return 0;}","7 1\n"),
  Arguments.of("before-dynamic","int twice(int n){return n*2;}extern int(*pointer)(int);int read(){return pointer(6);}int observed=read();int(*pointer)(int)=twice;int main(){printf(\"%d %d\\n\",observed,pointer(7));return 0;}","12 14\n")
 );}
 @ParameterizedTest(name="{0}") @MethodSource("programs")
 void functionAddressesAgree(String name,String source,String expected)throws Exception{
  CppReferenceTest.agree(temporary,name,"#include <stdio.h>\n"+source,expected);
 }
}
