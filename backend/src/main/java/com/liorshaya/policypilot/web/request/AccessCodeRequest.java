package com.liorshaya.policypilot.web.request;

/** The body of {@code POST /api/v1/auth/code}: the code the visitor typed. */
public record AccessCodeRequest(String code) {}
