/**
 * The HTTP layer (Document 2, API Surface): REST controllers and SSE endpoints under {@code /api/v1}, request and
 * response objects, the error envelope, the access-code front door, CSRF defenses, rate limiting and input
 * validation.
 *
 * <p>Allowed dependencies: every other package. Nothing depends on {@code web} (enforced by ArchUnit).
 * Sub-packages: {@code controller}, {@code request}, {@code response}, {@code error}, {@code security},
 * {@code validation}. Coverage gates: 95% line on {@code security} and {@code validation}, 70% elsewhere.
 */
package com.liorshaya.policypilot.web;
