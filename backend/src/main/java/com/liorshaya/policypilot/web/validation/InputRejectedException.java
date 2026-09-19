package com.liorshaya.policypilot.web.validation;

import com.liorshaya.policypilot.web.error.ErrorCode;

/** Input refused at the boundary, with the error code it is answered with and the problem in words (never the value). */
public class InputRejectedException extends RuntimeException {

    private final ErrorCode code;
    private final String problem;

    public InputRejectedException(ErrorCode code, String problem) {
        super(problem, null, false, false);
        this.code = code;
        this.problem = problem;
    }

    public ErrorCode code() {
        return code;
    }

    public String problem() {
        return problem;
    }
}
