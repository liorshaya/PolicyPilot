package com.liorshaya.policypilot.support;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The requirements of the Project Brief (FR-n, NFR-n) a test proves. The traceability matrix,
 * {@code docs/quality/traceability.md}, is generated from these annotations (Document 6, Traceability Matrix), so
 * a requirement without a tagged test shows up as an empty row. On a class it covers every test in the class.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface Requirement {

    /** Requirement ids exactly as the Brief writes them, for example {@code "FR-4"} or {@code "NFR-5"}. */
    String[] value();
}
