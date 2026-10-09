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
package org.lolaf.staffix.admin.server.web;

import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;
import org.lolaf.staffix.admin.server.connectors.TelemetrySources;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

import java.util.function.Supplier;

@UtilityClass
public class TelemetryProblems {

    /**
     * The sources of a scope's telemetry, which a query needs at least one of.
     *
     * @param what    {@code metrics} or {@code logs}, as the problems name them
     * @param resolve resolves the scope, refusing a connector whose settings cannot make a source
     */
    public static <S> TelemetrySources.Resolved<S> requireSources(String what, String engineId, @Nullable String group,
                                                                 @Nullable String sessionId, Supplier<TelemetrySources.Resolved<S>> resolve) {
        requireGroupForSession(group, sessionId);
        TelemetrySources.Resolved<S> resolved;
        try {
            resolved = resolve.get();
        } catch (IllegalArgumentException e) {
            throw connectorInvalid(what, e);
        }
        if (resolved.sources().isEmpty()) {
            throw notCollected(what, engineId, group, sessionId);
        }
        return resolved;
    }

    /**
     * A 404, so the UI tells telemetry nobody collects apart from a failure.
     */
    public static ErrorResponseException notCollected(String what, String engineId, @Nullable String group, @Nullable String sessionId) {
        String scope = sessionId != null ? "session " + group + "." + sessionId + " of engine " + engineId
                : group != null ? "group " + group + " of engine " + engineId
                : "engine " + engineId;
        return new ErrorResponseException(HttpStatus.NOT_FOUND, ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,
                "The " + what + " of " + scope + " are not collected: no " + what + " connector is assigned to its emitters, or they "
                        + "export nothing a backend reads"), null);
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
