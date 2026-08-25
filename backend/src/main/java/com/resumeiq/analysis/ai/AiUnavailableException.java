package com.resumeiq.analysis.ai;

import com.resumeiq.common.exception.ApiException;
import com.resumeiq.common.exception.ErrorCode;

/**
 * The provider could not be reached, or did not answer in time.
 *
 * <p>Maps to 503, which is the honest status: nothing is wrong with the request and retrying later is
 * a reasonable thing to do.
 *
 * <p>In this product it is usually not fatal. The analysis pipeline catches it, falls back to the
 * offline writer, and returns a complete result with computed scores and deterministic advice — because
 * the scores never depended on the provider in the first place. It surfaces to the user only when
 * something has explicitly asked for model-written advice and nothing else will do.
 *
 * <p>Messages here are written for a user, not a log reader, and never contain the URL, the key or any
 * part of the prompt. The cause carries the technical detail to the log.
 */
public class AiUnavailableException extends ApiException {

    public AiUnavailableException(String message) {
        super(ErrorCode.AI_UNAVAILABLE, message);
    }

    public AiUnavailableException(String message, Throwable cause) {
        super(ErrorCode.AI_UNAVAILABLE, message, cause);
    }
}
