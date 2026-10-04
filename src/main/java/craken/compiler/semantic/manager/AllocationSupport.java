package craken.compiler.semantic.manager;

import craken.SourceRange;
import craken.compiler.lexer.token.TokenType;
import craken.compiler.parser.node.Declaration.*;
import craken.compiler.parser.node.Expression;
import craken.compiler.parser.node.Expression.*;
import craken.compiler.parser.node.Statement;
import craken.compiler.parser.node.Statement.*;
import craken.compiler.type.CrakenType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Default Windows allocation ABI. The core AST also runs unchanged in the debugger.
 * Arrays and over-aligned objects carry the original malloc pointer and array count.
 * As in the existing .mh allocators, exhaustion terminates (exceptions are not supported).
 */
final class AllocationSupport {
    interface Context {
        String fresh(String label);
        void function(FunctionDecl function);
    }
    private static final CrakenType SIZE = CrakenType.UNSIGNED_LONG_LONG;
    private static final CrakenType POINTER = CrakenType.VOID.pointerTo();
    private final Context context;
    private final Map<String, String> allocators = new HashMap<>();
    private boolean declared;

    AllocationSupport(Context context) { this.context = context; }

    Expression allocate(Expression count, long size, long alignment, boolean array, String allocator, SourceRange r) {
        declare(r);
        boolean cookie = array || alignment > 16;
        long align = Math.max(16, alignment), overhead = cookie ? 16 + align - 1 : 0;
        String key = size + ":" + align + ":" + cookie + ":" + allocator;
        String function = allocators.get(key);
        if (function == null) {
            function = context.fresh("allocate"); allocators.put(key, function);
            String n = context.fresh("count"), raw = context.fresh("allocation"), ptr = context.fresh("payload");
            List<Statement> body = new ArrayList<>();
            body.add(new IfStmt(binary(name(n,r), TokenType.GREATER, integer((Long.MAX_VALUE-overhead)/size,r),r),
                    new ExprStmt(call("abort", List.of(),r),r), null,r));
            Expression bytes = binary(binary(name(n,r),TokenType.STAR,integer(size,r),r),TokenType.PLUS,integer(overhead,r),r);
            body.add(new VarDeclStmt(raw,POINTER,call(allocator==null?"malloc":allocator,List.of(new ConditionalExpr(bytes,bytes,integer(1,r),r)),r),r));
            body.add(new IfStmt(new UnaryExpr(TokenType.BANG,name(raw,r),r),new ExprStmt(call("abort",List.of(),r),r),null,r));
            if (cookie) {
                Expression start=binary(new CastExpr(SIZE,name(raw,r),r),TokenType.PLUS,integer(overhead,r),r);
                Expression aligned=binary(binary(start,TokenType.SLASH,integer(align,r),r),TokenType.STAR,integer(align,r),r);
                body.add(new VarDeclStmt(ptr,POINTER,new CastExpr(POINTER,aligned,r),r));
                body.add(new ExprStmt(new AssignmentExpr(slot(name(ptr,r),-2,r),TokenType.EQUAL,new CastExpr(SIZE,name(raw,r),r),r),r));
                body.add(new ExprStmt(new AssignmentExpr(slot(name(ptr,r),-1,r),TokenType.EQUAL,name(n,r),r),r));
            }
            body.add(new ReturnStmt(name(cookie?ptr:raw,r),r));
            context.function(new FunctionDecl(function,POINTER,List.of(new Parameter(n,SIZE,r)),false,new BlockStmt(body,r),false,r));
        }
        return call(function,List.of(new CastExpr(SIZE,count,r)),r);
    }

    Expression count(Expression pointer, SourceRange r) { return slot(pointer,-1,r); }
    Expression release(Expression pointer, long size, long alignment, boolean array, String deallocator, boolean sized, SourceRange r) {
        declare(r);
        Expression raw=array || alignment>16 ? new CastExpr(POINTER,slot(pointer,-2,r),r) : new CastExpr(POINTER,pointer,r);
        Expression bytes=array?binary(count(pointer,r),TokenType.STAR,integer(size,r),r):integer(size,r);
        if(array||alignment>16)bytes=binary(bytes,TokenType.PLUS,integer(16+Math.max(16,alignment)-1,r),r);
        return call(deallocator==null?"free":deallocator,sized?List.of(raw,bytes):List.of(raw),r);
    }
    Expression invalidBound(Expression count,int minimum,SourceRange r) {
        declare(r);
        return new ConditionalExpr(binary(count,TokenType.LESS,integer(minimum,r),r),call("abort",List.of(),r),
                new CastExpr(CrakenType.VOID,integer(0,r),r),r);
    }
    private void declare(SourceRange r) {
        if(declared)return; declared=true;
        context.function(new FunctionDecl("malloc",POINTER,List.of(new Parameter("size",SIZE,r)),false,null,true,r));
        context.function(new FunctionDecl("free",CrakenType.VOID,List.of(new Parameter("pointer",POINTER,r)),false,null,true,r));
        context.function(new FunctionDecl("abort",CrakenType.VOID,List.of(),false,null,true,true,r));
    }
    private static Expression slot(Expression pointer,int index,SourceRange r) {
        return new IndexExpr(new CastExpr(SIZE.pointerTo(),pointer,r),new IntegerLiteralExpr(index,Integer.toString(index),r),r);
    }
    private static Expression integer(long n,SourceRange r) { return new IntegerConstantExpr(n,SIZE,Long.toUnsignedString(n)+"ULL",r); }
    private static Expression name(String n,SourceRange r) { return new NameExpr(n,r); }
    private static Expression binary(Expression a,TokenType op,Expression b,SourceRange r) { return new BinaryExpr(a,op,b,r); }
    private static Expression call(String n,List<Expression> args,SourceRange r) { return new CallExpr(name(n,r),args,r); }
}
