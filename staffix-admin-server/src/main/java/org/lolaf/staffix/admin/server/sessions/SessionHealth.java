package org.lolaf.staffix.admin.server.sessions;

/**
 * Drives the session box colour: green, yellow, red in declaration order.
 */
public enum SessionHealth {
    LOGGED_IN,
    LOGGED_OUT_OUTSIDE_SESSION_TIME,
    LOGGED_OUT_WITHIN_SESSION_TIME
}
