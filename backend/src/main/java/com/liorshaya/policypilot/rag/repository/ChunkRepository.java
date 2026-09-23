package com.liorshaya.policypilot.rag.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
    /** Document 4, Fusion: the vector top list, nearest first; the order is total so a tie never depends on a plan. */
    private static final String VECTOR_TOP = """
            select kind, ref_id, text, 1 - (embedding <=> cast(:question as vector)) as score
            from chunk where ruleset_version_id = :version
            order by embedding <=> cast(:question as vector), kind, ref_id
            limit :limit""";
    /**
     * Document 4, Lexical index and Query: the question's words joined by OR ({@code plainto_tsquery} quotes and
     * escapes each lexeme, and its {@code &} becomes {@code |}), ranked by {@code ts_rank} plus the rank of the strong
     * terms alone, so a rule id, a field name or a number counts twice. Every value is bound.
     */
    private static final String LEXICAL_TOP = """
            with query as (
                select to_tsquery('simple', replace(plainto_tsquery('simple', :words)::text, ' & ', ' | ')) as words,
                       to_tsquery('simple', replace(plainto_tsquery('simple', :strong)::text, ' & ', ' | ')) as strong)
            select kind, ref_id, text, ts_rank(tsv, query.words) + ts_rank(tsv, query.strong) as score
            from chunk, query
            where ruleset_version_id = :version and tsv @@ query.words
            order by score desc, kind, ref_id
            limit :limit""";
    /**
     * Document 4, Prompt 5, Candidate selection: the rules of a version nearest a text, nearest first, a tie in the
     * order of their ids.
     */
    private static final String RULES_NEAREST = """
            select kind, ref_id, text, 1 - (embedding <=> cast(:text as vector)) as score
            from chunk where ruleset_version_id = :version and kind = 'RULE'
            order by embedding <=> cast(:text as vector), ref_id
            limit :limit""";
    private static final String BY_KIND_AND_REF = """
            select kind, ref_id, text, 0 as score from chunk
            where ruleset_version_id = :version and kind = :kind and ref_id = :ref""";
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

    public List<ScoredChunk> vectorTop(UUID versionId, float[] question, int limit) {
        return jdbc.sql(VECTOR_TOP).param("version", versionId).param("question", question).param("limit", limit)
                .query(ChunkRepository::scored).list();
    }

    public List<ScoredChunk> rulesNearest(UUID versionId, float[] text, int limit) {
        return jdbc.sql(RULES_NEAREST).param("version", versionId).param("text", text).param("limit", limit)
                .query(ChunkRepository::scored).list();
    }

    /** {@code words} and {@code strong} are lexically normalized text; either may be empty. */
    public List<ScoredChunk> lexicalTop(UUID versionId, String words, String strong, int limit) {
        return jdbc.sql(LEXICAL_TOP).param("version", versionId).param("words", words).param("strong", strong)
                .param("limit", limit).query(ChunkRepository::scored).list();
    }

    public Optional<ScoredChunk> find(UUID versionId, String kind, String refId) {
        return jdbc.sql(BY_KIND_AND_REF).param("version", versionId).param("kind", kind).param("ref", refId)
                .query(ChunkRepository::scored).optional();
    }

    private static ScoredChunk scored(ResultSet row, int number) throws SQLException {
        return new ScoredChunk(row.getString("kind"), row.getString("ref_id"), row.getString("text"),
                row.getDouble("score"));
    }

    /** The {@code n} of the column's {@code vector(n)}, which the migration took from the active profile. */
    public int embeddingDimension() {
        return jdbc.sql(COLUMN_DIMENSION).query(Integer.class).single();
    }
}
