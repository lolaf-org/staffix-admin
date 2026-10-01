package org.lolaf.staffix.admin.server.engines;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * An engine operation that failed, carrying the problem details the UI shows the operator.
 */
public class GatewayException extends ErrorResponseException {

    private GatewayException(HttpStatus status, String detail, Throwable cause) {
        super(status, ProblemDetail.forStatusAndDetail(status, detail), cause);
    }

    public static GatewayException unknownEngine(String instanceId) {
        return new GatewayException(HttpStatus.NOT_FOUND, "Unknown engine '" + instanceId + "'", null);
    }

    public static GatewayException unknownSession(String instanceId, String sessionId) {
        return new GatewayException(HttpStatus.NOT_FOUND,
                "Engine '" + instanceId + "' has no session '" + sessionId + "'", null);
    }

    /**
     * The request itself is wrong, e.g. a malformed FIX message.
     */
    public static GatewayException invalid(String detail) {
        return new GatewayException(HttpStatus.BAD_REQUEST, detail, null);
    }

    /**
     * The request is valid but the session's current state forbids it, e.g. sending on a logged out session.
     */
    public static GatewayException rejected(String detail) {
        return new GatewayException(HttpStatus.CONFLICT, detail, null);
    }

    public static GatewayException unavailable(String instanceId, Throwable cause) {
        return new GatewayException(HttpStatus.BAD_GATEWAY,
                "Engine '" + instanceId + "' did not answer: " + cause.getMessage(), cause);
    }
}
