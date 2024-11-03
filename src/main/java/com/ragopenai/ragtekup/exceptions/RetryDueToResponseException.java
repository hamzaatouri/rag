package com.ragopenai.ragtekup.exceptions;

import org.springframework.ai.retry.NonTransientAiException;

public class RetryDueToResponseException extends NonTransientAiException {
    public RetryDueToResponseException(String message) {
        super(message);
    }
}
