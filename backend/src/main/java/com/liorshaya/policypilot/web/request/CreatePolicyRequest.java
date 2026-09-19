package com.liorshaya.policypilot.web.request;

/**
 * The JSON body of {@code POST /api/v1/policies} (Document 2, API Surface): a title, the policy language ({@code he}
 * or {@code en}) and the pasted text. The upload form carries the same fields with a file instead of the text.
 */
public record CreatePolicyRequest(String title, String language, String text) {}
