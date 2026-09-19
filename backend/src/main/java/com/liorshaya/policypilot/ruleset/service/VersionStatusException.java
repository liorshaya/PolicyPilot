package com.liorshaya.policypilot.ruleset.service;

/**
 * The version's status does not allow what was asked: editing or publishing a version that is not a DRAFT, publishing
 * a protected version, or deciding on a version that is not PUBLISHED. The API answers 409
 * {@code VERSION_STATUS_CONFLICT}.
 */
public class VersionStatusException extends RuntimeException {

    public VersionStatusException(String message) {
        super(message, null, false, false);
    }
}
