package com.liorshaya.policypilot.web.security;

import java.time.Instant;
import java.util.UUID;

/**
 * The authenticated principal of a request: the visitor's sandbox and when its cookie was issued. The sandbox id
 * comes from the signed cookie only, never from the request (Document 5, Authorization (sandbox)).
 */
public record SandboxSession(UUID sandboxId, Instant issuedAt) {}
