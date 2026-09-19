package com.liorshaya.policypilot.web.error;

import java.io.IOException;

/** A request body passed its size limit while being read; answered as 413 {@code PAYLOAD_TOO_LARGE}. */
public class PayloadTooLargeException extends IOException {

    public PayloadTooLargeException() {
        super("request body over its limit", null);
    }
}
