package com.liorshaya.policypilot.architecture;

import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.belongToAnyOf;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.nameMatching;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.EntityManager;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.data.jpa.repository.Query;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;

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

    /** Document 7, day 3: no clock in engine, read directly or through a {@code now()} of java.time (NFR-1). */
    @ArchTest
    static final ArchRule engineReadsNoClock = noClasses()
            .that().resideInAnyPackage(PackageRulesTest.ROOT + ".engine..")
            .should().callMethodWhere(target(owner(assignableTo(System.class)))
                    .and(target(name("currentTimeMillis")).or(target(name("nanoTime")))))
            .orShould().callMethodWhere(target(owner(resideInAPackage("java.time.."))).and(target(name("now"))))
            .because("NFR-1: a decision may not depend on when it is made");

    /**
     * Document 5, SQL injection; Work Plan day 8: "no createNativeQuery outside rag and only with parameters". Native
     * SQL, through JPA, a native Spring Data query or Spring JDBC, lives only in {@code rag}.
     */
    @ArchTest
    static final ArchRule nativeSqlLivesOnlyInRag = noClasses()
            .that().resideOutsideOfPackage(PackageRulesTest.ROOT + ".rag..")
            .should().callMethodWhere(target(owner(assignableTo(EntityManager.class)))
                    .and(target(name("createNativeQuery"))))
            .orShould().dependOnClassesThat()
                    .belongToAnyOf(JdbcClient.class, JdbcTemplate.class, NamedParameterJdbcTemplate.class)
            .because("Document 5, SQL injection: the one place that writes SQL by hand is the one that is reviewed");

    @ArchTest
    static final ArchRule noNativeSpringDataQueries = noMethods()
            .should(beAnnotatedWithANativeQuery())
            .because("Document 5, SQL injection: repositories outside rag speak JPQL, and rag uses JdbcClient");

    /**
     * The second half of the rule: a class that runs SQL builds none of it. {@code +} compiles to an invokedynamic
     * that ArchUnit does not see, so Semgrep's {@code policypilot-java-no-sql-string-concatenation} covers that one.
     */
    @ArchTest
    static final ArchRule sqlIsNeverBuiltFromStrings = noClasses()
            .that(useJdbc())
            .should().callMethodWhere(target(owner(assignableTo(String.class)))
                    .and(target(nameMatching("format|formatted|concat|join|replace|repeat"))))
            .orShould().dependOnClassesThat().belongToAnyOf(StringBuilder.class, StringBuffer.class)
            .because("Document 5, SQL injection: values reach SQL as bound parameters, never as text");

    private static DescribedPredicate<JavaClass> useJdbc() {
        return DescribedPredicate.describe("run SQL through Spring JDBC", javaClass -> javaClass
                .getDirectDependenciesFromSelf().stream()
                .anyMatch(dependency -> dependency.getTargetClass().isAssignableTo(JdbcClient.class)
                        || dependency.getTargetClass().isAssignableTo(JdbcTemplate.class)
                        || dependency.getTargetClass().isAssignableTo(NamedParameterJdbcTemplate.class)));
    }

    private static ArchCondition<JavaMethod> beAnnotatedWithANativeQuery() {
        return new ArchCondition<>("be annotated with @Query(nativeQuery = true)") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                method.tryGetAnnotationOfType(Query.class)
                        .filter(Query::nativeQuery)
                        .ifPresent(query -> events.add(SimpleConditionEvent.violated(method,
                                method.getFullName() + " declares a native query")));
            }
        };
    }
}
