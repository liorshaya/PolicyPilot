package com.liorshaya.policypilot.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class of every {@code *IT} test: one PostgreSQL container with pgvector, started once per JVM and shared by
 * every Spring test context through {@link ServiceConnection} (Document 6, Backend Test Design). The image is the
 * one Docker Compose and Railway run, so Flyway migrations are proven against the real database.
 */
public abstract class PostgresContainerSupport {

    public static final DockerImageName PGVECTOR_IMAGE =
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres");

    @ServiceConnection
    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(PGVECTOR_IMAGE);

    static {
        POSTGRES.start();
    }
}
