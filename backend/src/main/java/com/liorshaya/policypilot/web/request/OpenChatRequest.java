package com.liorshaya.policypilot.web.request;

import com.liorshaya.policypilot.web.error.ApiException;
import com.liorshaya.policypilot.web.error.ErrorCode;
import com.liorshaya.policypilot.web.error.ErrorDetail;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** The body of {@code POST /chat/sessions} (Document 2, API Surface): the rule set and the version to talk about. */
public record OpenChatRequest(@Nullable UUID rulesetId, @Nullable Integer versionNo) {

    public UUID requiredRulesetId() {
        if (rulesetId == null) {
            throw new ApiException(ErrorCode.REQUEST_INVALID, List.of(new ErrorDetail("/rulesetId", "is missing")));
        }
        return rulesetId;
    }

    public int requiredVersionNo() {
        if (versionNo == null || versionNo < 1) {
            throw new ApiException(ErrorCode.REQUEST_INVALID,
                    List.of(new ErrorDetail("/versionNo", "is not a version number")));
        }
        return versionNo;
    }
}
