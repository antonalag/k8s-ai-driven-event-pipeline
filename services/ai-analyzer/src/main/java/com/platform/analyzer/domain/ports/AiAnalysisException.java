package com.platform.analyzer.domain.ports;

/**
 * Unchecked exception thrown when an AI language model adapter fails.
 */
public class AiAnalysisException extends RuntimeException {

    public AiAnalysisException(String message) {
        super(message);
    }

    public AiAnalysisException(String message, Throwable cause) {
        super(message, cause);
    }
}
