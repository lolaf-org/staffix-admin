package org.lolaf.staffix.admin.server.web;

import lombok.experimental.UtilityClass;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

@UtilityClass
public class TelemetryProblems {

    public static ErrorResponseException notConfigured(String what) {
        return new ErrorResponseException(HttpStatus.SERVICE_UNAVAILABLE, ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE, "No " + what + " backend is configured"), null);
    }

    public static ErrorResponseException backendFailed(String what, RuntimeException cause) {
        return new ErrorResponseException(HttpStatus.BAD_GATEWAY, ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_GATEWAY, "The " + what + " backend failed: " + cause.getMessage()), cause);
    }
}
