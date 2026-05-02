package org.example.agent.tool;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ManagedTool {

    String name();

    String displayName();

    String description();

    String riskLevel() default "LOW";

    String instruction() default "";

    boolean defaultEnabled() default true;

    int order() default 1000;
}
