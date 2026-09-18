package com.liorshaya.policypilot.architecture;

import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.belongToAnyOf;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * APIs the code base never uses (Document 5, Injection table and Supply Chain; Work Plan day 1), plus the purity of
 * the engine (Document 2, Constraints: no clock, no random source, no I/O).
 */
@AnalyzeClasses(packages = PackageRulesTest.ROOT, importOptions = ImportOption.DoNotIncludeTests.class)
class ForbiddenApisTest {

    @ArchTest
    static final ArchRule noProcessBuilder = noClasses()
            .should().dependOnClassesThat().belongToAnyOf(ProcessBuilder.class)
            .because("Document 5: no process execution from the API");

    @ArchTest
    static final ArchRule noRuntimeExec = noClasses()
            .should().callMethodWhere(target(owner(assignableTo(Runtime.class))).and(target(name("exec"))))
            .because("Document 5: no process execution from the API");

    @ArchTest
    static final ArchRule noScriptEngines = noClasses()
            .should().dependOnClassesThat().resideInAnyPackage("javax.script..")
            .because("Document 5: expressions are tree-evaluated, never scripted");

    @ArchTest
    static final ArchRule noPolymorphicJacksonTyping = noClasses()
            .should().dependOnClassesThat().belongToAnyOf(JsonTypeInfo.class)
            .because("Document 5: strict Jackson without polymorphic type handling (deserialization injection)");

    @ArchTest
    static final ArchRule engineHasNoClockRandomOrIo = noClasses()
            .that().resideInAnyPackage(PackageRulesTest.ROOT + ".engine..")
            .should().dependOnClassesThat(belongToAnyOf(Clock.class, Random.class, SecureRandom.class, ThreadLocalRandom.class)
                    .or(resideInAnyPackage("java.net..", "java.nio.file..", "java.io..")))
            .because("NFR-1: the same case against the same version always yields the same decision and trace");
}
