package com.liorshaya.policypilot.web.request;

import com.liorshaya.policypilot.ai.service.ExplainService.Audience;
import com.liorshaya.policypilot.web.error.ApiException;
import com.liorshaya.policypilot.web.error.ErrorCode;
import com.liorshaya.policypilot.web.error.ErrorDetail;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The body of {@code POST /decisions/{id}/explain} (Document 2, API Surface): who reads the explanation, the officer
 * when the body names nobody (Document 4, Prompt 3).
 */
public record ExplainRequest(@Nullable @Schema(allowableValues = {"officer", "applicant"}) String audience) {

    /** The audience named, the officer when none is; REQUEST_INVALID at {@code /audience} for any other name. */
    public Audience reader() {
        if (audience == null) {
            return Audience.OFFICER;
        }
        Audience named = Audience.of(audience);
        if (named == null) {
            throw new ApiException(ErrorCode.REQUEST_INVALID,
                    List.of(new ErrorDetail("/audience", "is not officer or applicant")));
        }
        return named;
    }
}
