package com.resumeiq.resume.extract;

import com.resumeiq.common.exception.ApiException;
import com.resumeiq.common.exception.ErrorCode;

/**
 * The file was the right kind of thing but could not be turned into text.
 *
 * <p>Three situations reach this: the parser refused the bytes (truncated or corrupt),
 * the document forbids text extraction or needs a password, or it parsed perfectly and
 * contained almost no text — which is what a scanned resume looks like from here, a
 * picture of words.
 *
 * <p>Every message this carries is written to be shown to the person who uploaded the
 * file, and says what to do next. None of them quote the file's contents.
 */
public class UnreadableResumeException extends ApiException {

    public UnreadableResumeException(String message) {
        super(ErrorCode.UNREADABLE_FILE, message);
    }

    /**
     * Keeps the parser's exception as the cause so it reaches the log, while the message
     * shown to the user stays ours. The two are deliberately different: a parser message
     * can quote the bytes it choked on, and on this API those bytes are somebody's resume.
     */
    public UnreadableResumeException(String message, Throwable cause) {
        super(ErrorCode.UNREADABLE_FILE, message, cause);
    }
}
