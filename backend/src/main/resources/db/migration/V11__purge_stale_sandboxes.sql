-- V11: the stale sandbox purge (Document 2, Security and Demo Protections, Sandbox isolation; Document 5, principle
-- 1 and Data Protection, Deletion; decided 2026-09-27, day 15). The API role holds no delete grant on rule sets,
-- versions, rules, chat or the audit log, and keeps none: this procedure, owned by the migration role and run with
-- its rights, is the one path that deletes them, and it deletes only whole sandboxes idle for 24 hours.
--
-- A sandbox's activity is the newest timestamp among its rows. The 24 hours are measured against the earlier of the
-- API's clock and the database's, so the API can narrow the window (a test clock in the past) but never widen it.
-- The procedure takes no sandbox id and never deletes a protected row (sandbox_id is null on every one of them), nor
-- an audit entry on a protected version unless a change request of the deleted sandbox wrote it. It returns what it
-- deleted as a JSON object in its OUT parameter, for the RESET audit entry and the demo.reset log line. A procedure, not
-- a function, because the API calls it by name through JPA, which issues CALL (no SQL outside rag: Document 5,
-- Verification, Architecture); PostgreSQL 14 and later give a procedure OUT parameters.
CREATE PROCEDURE purge_stale_sandboxes(api_now timestamptz, OUT purged text)
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, public AS $$
DECLARE
    cutoff    timestamptz := least(coalesce(api_now, now()), now()) - interval '24 hours';
    stale     uuid[];
    versions  uuid[];
    requests  uuid[];
    sessions  uuid[];
    counts    jsonb;
    deleted   integer;
BEGIN
    WITH activity (sandbox_id, at) AS (
        SELECT sandbox_id, created_at FROM policy_document WHERE sandbox_id IS NOT NULL
        UNION ALL
        SELECT d.sandbox_id, v.created_at FROM policy_version v JOIN policy_document d ON d.id = v.document_id
            WHERE d.sandbox_id IS NOT NULL
        UNION ALL
        SELECT sandbox_id, created_at FROM ruleset WHERE sandbox_id IS NOT NULL
        UNION ALL
        SELECT r.sandbox_id, v.published_at FROM ruleset_version v JOIN ruleset r ON r.id = v.ruleset_id
            WHERE r.sandbox_id IS NOT NULL
        UNION ALL
        SELECT sandbox_id, decided_at FROM decision
        UNION ALL
        SELECT sandbox_id, created_at FROM chat_session
        UNION ALL
        SELECT s.sandbox_id, m.at FROM chat_message m JOIN chat_session s ON s.id = m.session_id
        UNION ALL
        SELECT sandbox_id, greatest(created_at, decided_at) FROM change_request
        UNION ALL
        SELECT sandbox_id, NULL FROM case_fixture WHERE sandbox_id IS NOT NULL
    )
    SELECT coalesce(array_agg(sandbox_id), '{}') INTO stale
    FROM (SELECT sandbox_id FROM activity GROUP BY sandbox_id
          HAVING coalesce(max(at), '-infinity') < cutoff) AS idle;

    SELECT coalesce(array_agg(v.id), '{}') INTO versions
        FROM ruleset_version v JOIN ruleset r ON r.id = v.ruleset_id WHERE r.sandbox_id = ANY (stale);
    SELECT coalesce(array_agg(id), '{}') INTO requests FROM change_request WHERE sandbox_id = ANY (stale);
    SELECT coalesce(array_agg(id), '{}') INTO sessions FROM chat_session WHERE sandbox_id = ANY (stale);
    counts := jsonb_build_object('sandboxes', cardinality(stale), 'versions', cardinality(versions),
        'changeRequests', cardinality(requests), 'chatSessions', cardinality(sessions));

    -- in the order the foreign keys allow: what points at a version or a request goes first
    DELETE FROM audit_entry WHERE ruleset_version_id = ANY (versions) OR change_request_id = ANY (requests);
    GET DIAGNOSTICS deleted = ROW_COUNT;
    counts := counts || jsonb_build_object('auditEntries', deleted);
    DELETE FROM chat_message WHERE session_id = ANY (sessions);
    DELETE FROM chat_session WHERE id = ANY (sessions);
    DELETE FROM decision WHERE sandbox_id = ANY (stale);
    GET DIAGNOSTICS deleted = ROW_COUNT;
    counts := counts || jsonb_build_object('decisions', deleted);
    DELETE FROM change_request WHERE id = ANY (requests);
    DELETE FROM chunk WHERE ruleset_version_id = ANY (versions);
    DELETE FROM rule WHERE ruleset_version_id = ANY (versions);
    DELETE FROM ruleset_version WHERE id = ANY (versions);
    DELETE FROM ruleset WHERE sandbox_id = ANY (stale);
    DELETE FROM case_fixture WHERE sandbox_id = ANY (stale);
    DELETE FROM policy_document WHERE sandbox_id = ANY (stale);
    GET DIAGNOSTICS deleted = ROW_COUNT;
    purged := (counts || jsonb_build_object('policies', deleted))::text;
END
$$;

REVOKE ALL ON PROCEDURE purge_stale_sandboxes(timestamptz, text) FROM PUBLIC;
GRANT EXECUTE ON PROCEDURE purge_stale_sandboxes(timestamptz, text) TO policypilot_app;
