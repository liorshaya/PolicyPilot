package com.liorshaya.policypilot.rag.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * The pgvector chunk store (Document 2, Data Model, {@code chunk}; ADR-5). SQL is written by hand here because JPA
 * has no vector or full-text types, and every value is a bound parameter (Document 5, SQL injection): the text, the
 * vector as a {@code real[]} cast to {@code vector}, and the lexical text {@code to_tsvector} reads.
 */
@Repository
public class ChunkRepository {

    private static final String DELETE_VERSION = "delete from chunk where ruleset_version_id = :version";
    private static final String INSERT = """
            insert into chunk (id, ruleset_version_id, kind, ref_id, text, embedding, tsv, created_at)
            values (:id, :version, :kind, :ref, :text, cast(:embedding as vector),
                    to_tsvector('simple', :lexical), :at)""";
    private static final String COLUMN_DIMENSION = """
            select atttypmod from pg_attribute where attrelid = 'chunk'::regclass and attname = 'embedding'""";

    private final JdbcClient jdbc;

    public ChunkRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Replaces a version's chunks in one transaction, so a version never has half of an old and half of a new set. */
    @Transactional
    public void replace(UUID versionId, List<ChunkRow> rows, Instant at) {
        jdbc.sql(DELETE_VERSION).param("version", versionId).update();
        for (ChunkRow row : rows) {
            jdbc.sql(INSERT)
                    .param("id", UUID.randomUUID())
                    .param("version", versionId)
                    .param("kind", row.kind())
                    .param("ref", row.refId())
                    .param("text", row.text())
                    .param("embedding", row.embedding())
                    .param("lexical", row.lexical())
                    .param("at", Timestamp.from(at))
                    .update();
        }
    }

    /** The {@code n} of the column's {@code vector(n)}, which the migration took from the active profile. */
    public int embeddingDimension() {
        return jdbc.sql(COLUMN_DIMENSION).query(Integer.class).single();
    }
}
