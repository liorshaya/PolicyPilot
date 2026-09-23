/**
 * The change use case's reads (Document 2, Flow 4; Document 4, Prompt 5): the published version a request is proposed
 * against and its candidate rules, from the rule set store and the chunk store. It reads the database, so its tests
 * are integration tests; the prompt and the repair loop need none and live in {@code ai.service}, on the unit-test
 * gate, as {@code ai.chat} and {@code ai.service.chat} split the chat.
 */
package com.liorshaya.policypilot.ai.change;
