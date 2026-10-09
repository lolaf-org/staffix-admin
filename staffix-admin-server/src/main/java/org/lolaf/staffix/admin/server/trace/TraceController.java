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
package org.lolaf.staffix.admin.server.trace;

import lombok.RequiredArgsConstructor;
import org.lolaf.staffix.admin.server.connectors.Connectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class TraceController {

    private final Connectors connectors;

    /**
     * Read from the store on every call, so a hand edit of the stored settings applies without a restart.
     */
    @GetMapping("/api/trace/settings")
    public TraceSettings getTraceSettings() {
        return TraceSettings.of(connectors.load().getTrace());
    }

    /**
     * A 500: the stored file is wrong, not the request.
     */
    @ExceptionHandler(InvalidTraceSettingsException.class)
    ProblemDetail invalid(InvalidTraceSettingsException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        problem.setProperty("problems", e.getProblems());
        return problem;
    }
}
