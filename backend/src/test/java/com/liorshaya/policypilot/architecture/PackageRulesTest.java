package com.liorshaya.policypilot.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.liorshaya.policypilot.config.PolicyPilotProperties;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Every arrow of the module table in Document 2, "Backend Module Structure", as a rule: a package may depend only
 * on the packages its row lists. Adding an arrow means changing the document first, then this test.
 */
@AnalyzeClasses(packages = PackageRulesTest.ROOT, importOptions = ImportOption.DoNotIncludeTests.class)
class PackageRulesTest {

    static final String ROOT = "com.liorshaya.policypilot";

    /** {@code rules} and {@code engine} stay pure: no Spring, no configuration, no shared helpers. */
    private static final Set<String> PURE_MODULES = Set.of("rules", "engine");

    /** What the pure modules may import from outside the application: the JDK, Jackson, the schema validator, RE2J. */
    private static final String[] PURE_EXTERNALS = {
        "java..",
        "com.fasterxml.jackson..",
        "tools.jackson..",
        "com.networknt.schema..",
        "com.google.re2j..",
        "org.jspecify.."
    };

    @ArchTest
    static final ArchRule rulesDependsOnlyOnJdkAndJackson = classes()
            .that().resideInAPackage(pkg("rules"))
            .should().onlyDependOnClassesThat(resideInAnyPackage(PURE_EXTERNALS).or(resideInAPackage(pkg("rules"))))
            .as("rules may depend only on the JDK, Jackson, the schema validator and RE2J")
            .because("Document 2, Backend Module Structure: rules -> JDK, Jackson");

    @ArchTest
    static final ArchRule engineDependsOnlyOnJdkAndRules = classes()
            .that().resideInAPackage(pkg("engine"))
            .should().onlyDependOnClassesThat(
                    resideInAnyPackage(PURE_EXTERNALS).or(resideInAnyPackage(pkg("rules"), pkg("engine"))))
            .as("engine may depend only on the JDK, RE2J and rules")
            .because("Document 2, Backend Module Structure: engine -> rules; NFR-1 determinism");

    @ArchTest
    static final ArchRule policy = moduleRule("policy", "rules");

    @ArchTest
    static final ArchRule decision = moduleRule("decision", "engine", "rules");

    @ArchTest
    static final ArchRule ai = moduleRule("ai", "rules", "engine", "policy", "decision", "rag", "ai.adapter");

    @ArchTest
    static final ArchRule aiAdapter = moduleRule("ai.adapter", "ai");

    @ArchTest
    static final ArchRule rag = moduleRule("rag", "policy", "rules", "ai.adapter");

    @ArchTest
    static final ArchRule change = moduleRule("change", "ai", "engine", "decision", "audit");

    @ArchTest
    static final ArchRule audit = moduleRule("audit");

    @ArchTest
    static final ArchRule demo = moduleRule("demo", "policy", "decision", "audit");

    @ArchTest
    static final ArchRule nothingDependsOnWeb = noClasses()
            .that().resideOutsideOfPackage(pkg("web"))
            .should().dependOnClassesThat().resideInAPackage(pkg("web"))
            .because("Document 2, Backend Module Structure: web depends on every package, nothing depends on web");

    @ArchTest
    static final ArchRule onlyTheAdapterImportsSpringAi = noClasses()
            .that().resideOutsideOfPackage(pkg("ai.adapter"))
            .should().dependOnClassesThat().resideInAPackage("org.springframework.ai..")
            .because("NFR-4 provider independence: only ai.adapter may import org.springframework.ai");

    @ArchTest
    static final ArchRule crossCuttingPackagesDependOnNothingInTheApplication = classes()
            .that().resideInAnyPackage(pkg("config"), pkg("common"))
            .should().onlyDependOnClassesThat(
                    resideOutsideOfPackage(ROOT + "..").or(resideInAnyPackage(pkg("config"), pkg("common"))))
            .because("config and common are cross-cutting: they are depended on, they depend on no module");

    @ArchTest
    static final ArchRule commonDoesNotDependOnConfig = noClasses()
            .that().resideInAPackage(pkg("common"))
            .should().dependOnClassesThat().resideInAPackage(pkg("config"))
            .because("common holds dependency-free helpers");

    @ArchTest
    static final ArchRule nobodyDependsOnConfigurationClassesExceptTheProperties = noClasses()
            .that().resideOutsideOfPackage(pkg("config"))
            .should().dependOnClassesThat(resideInAPackage(pkg("config"))
                    .and(DescribedPredicate.not(JavaClass.Predicates.belongToAnyOf(PolicyPilotProperties.class))))
            .because("modules read the typed properties (the record and its nested records); the wiring classes are"
                    + " Spring's business");

    /** Document 2: cross-package access goes through service interfaces, never through another package's repository. */
    @ArchTest
    static void repositoriesArePrivateToTheirModule(JavaClasses appClasses) {
        for (String module : List.of("policy", "decision", "rag", "change", "audit", "demo")) {
            classes().that().resideInAPackage(ROOT + "." + module + ".repository..")
                    .should().onlyBeAccessed().byAnyPackage(ROOT + "." + module + "..")
                    .as(module + ".repository is private to " + module)
                    .check(appClasses);
        }
    }

    private static ArchRule moduleRule(String module, String... allowedModules) {
        List<String> allowedPackages = new ArrayList<>();
        allowedPackages.add(pkg(module));
        for (String allowed : allowedModules) {
            allowedPackages.add(pkg(allowed));
        }
        if (!PURE_MODULES.contains(module)) {
            allowedPackages.add(pkg("config"));
            allowedPackages.add(pkg("common"));
        }
        DescribedPredicate<JavaClass> permitted = resideOutsideOfPackage(ROOT + "..")
                .or(resideInAnyPackage(allowedPackages.toArray(String[]::new)));
        return classes()
                .that(inModule(module))
                .should().onlyDependOnClassesThat(permitted)
                .as("'" + module + "' may depend only on " + String.join(", ", allowedModules))
                .because("Document 2, Backend Module Structure");
    }

    /** The {@code ai} module means {@code ai} without {@code ai.adapter}; every other module is its whole package tree. */
    private static DescribedPredicate<JavaClass> inModule(String module) {
        if (module.equals("ai")) {
            return resideInAPackage(pkg("ai")).and(resideOutsideOfPackage(pkg("ai.adapter")));
        }
        return resideInAPackage(pkg(module));
    }

    private static String pkg(String module) {
        return ROOT + "." + module + "..";
    }
}
