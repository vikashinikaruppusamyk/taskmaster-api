package com.vikashini.taskmaster.ai;

import com.vikashini.taskmaster.common.ApiException;
import org.springframework.http.HttpStatus;

/** The AI features are switched off or the model could not answer; reported as 503. */
public class AiUnavailableException extends ApiException {

    public AiUnavailableException(String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, message);
    }
}
