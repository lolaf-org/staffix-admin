/*
 * Copyright © 2026 Lolaf.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

    public static GatewayException unknownDictionary(String engineId, String dictionaryId) {
        return new GatewayException(HttpStatus.NOT_FOUND, "Engine '" + engineId + "' has no dictionary '" + dictionaryId + "'", null);
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

    public static GatewayException notFound(String detail) {
        return new GatewayException(HttpStatus.NOT_FOUND, detail, null);
    }

    /**
     * The engine no longer accepts the token it announced, typically after a restart; its next announcement
     * brings the new one.
     */
    public static GatewayException tokenRefused(String engineId) {
        return new GatewayException(HttpStatus.BAD_GATEWAY,
                "Engine '" + engineId + "' refused its token; it was probably restarted and will announce a new one", null);
    }

    public static GatewayException failed(String engineId, String detail) {
        return new GatewayException(HttpStatus.BAD_GATEWAY, "Engine '" + engineId + "' failed: " + detail, null);
    }

    public static GatewayException unavailable(String engineId, Throwable cause) {
        return new GatewayException(HttpStatus.BAD_GATEWAY,
                "Engine '" + engineId + "' did not answer: " + cause.getMessage(), cause);
    }
}
