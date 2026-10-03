package org.lolaf.staffix.admin.server.web;

import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

@UtilityClass
public class TelemetryProblems {

    /**
     * A 404, so the UI tells telemetry nobody collects apart from a failure.
     */
    public static ErrorResponseException notCollected(String what, String engineId, @Nullable String group, @Nullable String sessionId) {
        String scope = sessionId != null ? "session " + group + "." + sessionId + " of engine " + engineId
                : group != null ? "group " + group + " of engine " + engineId
                : "engine " + engineId;
        return new ErrorResponseException(HttpStatus.NOT_FOUND, ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,
                "The " + what + " of " + scope + " are not collected: no " + what + " connector is assigned to its emitters"), null);
    }

    /**
     * Session ids are unique per group only, so one alone names no session.
     */
    public static void requireGroupForSession(String group, String sessionId) {
        if (sessionId != null && group == null) {
            throw new ErrorResponseException(HttpStatus.BAD_REQUEST, ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST, "'sessionId' needs its 'group'"), null);
        }
    }

    public static ErrorResponseException connectorInvalid(String what, IllegalArgumentException cause) {
        return new ErrorResponseException(HttpStatus.BAD_GATEWAY, ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_GATEWAY, "The " + what + " connector is invalid: " + cause.getMessage()), cause);
    }

    public static ErrorResponseException backendFailed(String what, RuntimeException cause) {
        return new ErrorResponseException(HttpStatus.BAD_GATEWAY, ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_GATEWAY, "The " + what + " backend failed: " + cause.getMessage()), cause);
    }
}
