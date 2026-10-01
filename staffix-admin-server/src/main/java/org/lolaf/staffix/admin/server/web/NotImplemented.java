package org.lolaf.staffix.admin.server.web;

import lombok.experimental.UtilityClass;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/**
 * Marks endpoints whose contract is defined but whose implementation lands in a later phase.
 */
@UtilityClass
public class NotImplemented {

    public static ErrorResponseException notImplemented() {
        return new ErrorResponseException(HttpStatus.NOT_IMPLEMENTED);
    }
}
