package craken.compiler.parser.node;

import java.lang.annotation.*;

/** Ordered business components of an AST class, excluding inherited visual metadata. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.CONSTRUCTOR)
public @interface AstNodeConstructor {
    String[] value();
}
