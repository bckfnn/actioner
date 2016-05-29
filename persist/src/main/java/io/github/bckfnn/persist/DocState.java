/*
 * Copyright 2014 [inno:vasion]
 */
package io.github.bckfnn.persist;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for the entries in enum used for document states (DocState).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.FIELD})
public @interface DocState {
    /**
     * The one character code for the enum state.
     * @return the doc state.
     */
    String code();

    /**
     * A text description for the enum state
     * @return the text description.
     */
    String label();

    boolean isDefault() default false;
}
