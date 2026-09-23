package com.liorshaya.policypilot.web.response;

import com.liorshaya.policypilot.web.response.VersionResponse.FindingResponse;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The data of the {@code error} event of a stream that ends with a document the validator judged (Document 2, API
 * Surface: the generation and the change request): the error code, the findings if there are any, and the model's
 * last document, so the analyst sees what went wrong.
 */
public record StreamFailure(String code, List<FindingResponse> findings, @Nullable Object document) {

    public StreamFailure {
        findings = List.copyOf(findings);
    }
}
