package com.resumeiq.analysis.ai;

import com.resumeiq.common.exception.ApiException;
import com.resumeiq.common.exception.ErrorCode;

/**
 * Something answered, and it was not usable.
 *
 * <p>Maps to 502 rather than 503, and the distinction from {@link AiUnavailableException} is worth
 * keeping: a timeout is worth retrying and a model that returned prose where JSON was asked for is
 * not going to do better on the second attempt for the same reason.
 *
 * <p>This is the expected failure of asking a language model for structured output, so it is a normal
 * control-flow event here rather than an emergency: fenced JSON, a trailing apology after the closing
 * brace, a truncated response when the token limit was hit mid-object, an array where an object
 * belonged. {@link AiAdviceReader} absorbs the recoverable ones and raises this for the rest, and the
 * pipeline responds by falling back to the offline writer.
 */
public class AiInvalidResponseException extends ApiException {

    public AiInvalidResponseException(String message) {
        super(ErrorCode.AI_INVALID_RESPONSE, message);
    }

    public AiInvalidResponseException(String message, Throwable cause) {
        super(ErrorCode.AI_INVALID_RESPONSE, message, cause);
    }
}
