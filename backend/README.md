# PolicyPilot backend

Java 21, Spring Boot 4.0.x, Spring AI 2.0.x, PostgreSQL 16 with pgvector. One Maven project, one deployable JAR,
one Docker image. The design is Document 2 (`docs/02-architecture.md`); this file only explains how the code is
laid out and how to run it.

## Layout

```
backend/
  pom.xml                         pinned versions, JaCoCo gates per package, PIT, Dependency-Check, SBOM profiles
  Dockerfile                      multi-stage, pinned images by digest, non-root runtime
  railway.json                    Railway build and deploy settings: Dockerfile builder, health check, restart policy,
                                  one replica, rebuild only when backend/ changes (RUNBOOK section 7.3)
  src/main/java/com/liorshaya/policypilot/
    PolicyPilotApplication.java
    config/                       Spring configuration and PolicyPilotProperties (prefix policypilot.)
    common/                       small dependency-free helpers
    rules/                        the Rules DSL model and validator (pure: JDK + Jackson)          day 2
    engine/                       the deterministic engine (pure: JDK + rules)                       day 3
    policy/    {service, entity, repository}                                                         day 4
    decision/  {service, entity, repository}                                                         day 5
    audit/     {service, entity, repository}                                                         day 5
    demo/      {service, entity, repository}                                                         days 4, 15
    ai/        gateways, prompts, use cases, validation loop;  ai/adapter (Spring AI), ai/cache      day 7
    rag/       {service, entity, repository}                                                         day 8
    change/    {service, entity, repository}                                                         days 12, 13
    web/       {controller, request, response, error, security, validation}                          day 4 onward
  src/main/resources/
    application.yml               defaults for every policypilot.* property; secrets from the environment only
    application-openai.yml        the OpenAI provider profile
    application-ollama.yml        the local Ollama provider profile
    application-cloud.yml         Railway additions (forwarded headers, JSON logs)
    db/migration/                 Flyway migrations (V1 enables pgvector)
    schemas/                      the Rules DSL JSON Schema, byte-identical to fixtures/schemas/ (CI checks it)
    prompts/                      the versioned prompts of Document 4 (from day 7)
  src/test/java/com/liorshaya/policypilot/
    architecture/                 ArchUnit: the module table, forbidden APIs, engine purity
    support/                      the shared Testcontainers pgvector database for *IT tests
    <package>/                    tests mirror the main packages (Document 6, Backend Test Design)
```

**Modules and layers.** The eleven module packages of Document 2 are the top level; the dependency direction
between them is inward toward `rules` and `engine` and is enforced by `PackageRulesTest`. Inside a module that
owns state, `service` holds the entry points other modules may call, `entity` the JPA entities and `repository`
the Spring Data repositories, which stay private to their module. The HTTP layer is the `web` package:
controllers, request and response records, the error envelope, security and input validation. `config` and
`common` are cross-cutting and depend on nothing else in the application.

## Commands

```
./mvnw verify                 unit + architecture tests, integration tests on Testcontainers, coverage gates
./mvnw verify -DskipITs       the fast subset (CI stage 2)
./mvnw -Pmutation -DskipITs test-compile org.pitest:pitest-maven:mutationCoverage     PIT (CI stage 3)
./mvnw spring-boot:run -Dspring-boot.run.profiles=openai      needs OPENAI_API_KEY and a local PostgreSQL
docker build -t policypilot-backend .                          the production image
```

The Maven wrapper downloads Maven 3.9.16 on first use (checksum pinned). Tests need Docker for the pgvector
container. Integration tests are `*IT` classes extending `PostgresContainerSupport`; unit tests are `*Test`.
A flaky test is tagged `@Tag("quarantine")`: it keeps running and reporting but no longer fails the build.
