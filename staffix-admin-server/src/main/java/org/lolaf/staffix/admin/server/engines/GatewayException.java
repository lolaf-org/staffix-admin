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

    public static GatewayException unknownEngine(String engineId) {
        return new GatewayException(HttpStatus.NOT_FOUND, "Unknown engine '" + engineId + "'", null);
    }

    public static GatewayException unknownSession(SessionRef session) {
        return new GatewayException(HttpStatus.NOT_FOUND, "Engine '" + session.getEngineId() + "' has no session '"
                + session.getGroup() + "." + session.getSessionId() + "'", null);
    }

    /**
     * The request itself is wrong, e.g. a malformed FIX message or a config the session does not have.
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

    public static GatewayException unavailable(String engineId, Throwable cause) {
        return new GatewayException(HttpStatus.BAD_GATEWAY,
                "Engine '" + engineId + "' did not answer: " + cause.getMessage(), cause);
    }
}
