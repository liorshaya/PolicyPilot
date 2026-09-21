package com.liorshaya.policypilot.web.controller;

/** The route constants of the API (Document 2, API Surface: a versioned REST API under {@code /api/v1}). */
public final class ApiPaths {

    public static final String V1 = "/api/v1";
    public static final String AUTH_CODE = V1 + "/auth/code";
    public static final String POLICIES = V1 + "/policies";
    public static final String POLICY = POLICIES + "/{id}";
    public static final String POLICY_RULESETS = POLICY + "/rulesets";
    public static final String RULESETS = V1 + "/rulesets";
    public static final String RULESET_VERSION = RULESETS + "/{id}/versions/{no}";
    public static final String RULESET_VERSION_RULES = RULESET_VERSION + "/rules";
    public static final String RULESET_VERSION_PUBLISH = RULESET_VERSION + "/publish";
    public static final String RULESET_VERSION_DECIDE = RULESET_VERSION + "/decide";
    public static final String RULESET_VERSION_STATS = RULESET_VERSION + "/stats";
    public static final String RULESET_VERSION_SIMULATE = RULESET_VERSION + "/simulate";
    public static final String RULESET_VERSION_RETRIEVAL = RULESET_VERSION + "/retrieval";
    public static final String CHAT_SESSIONS = V1 + "/chat/sessions";
    public static final String CHAT_MESSAGES = CHAT_SESSIONS + "/{id}/messages";
    public static final String DECISIONS = V1 + "/decisions";
    public static final String DECISION = DECISIONS + "/{id}";
    public static final String DECISION_EXPORT = DECISION + "/export";
    public static final String DOCS = "/api/docs";

    private ApiPaths() {}
}
