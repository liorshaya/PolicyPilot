/**
 * The chat use case (Document 2, Flow 3; Document 4, Prompt 4): sessions and their messages, and the tools that read
 * decisions and ask the engine for simulations. It reads and writes the database, so its tests are integration tests
 * (Document 6: every use case has one with a real database); everything of the chat that needs no database lives in
 * {@code ai.service.chat}, held to the unit-test gate.
 */
package com.liorshaya.policypilot.ai.chat;
