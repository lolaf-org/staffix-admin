package org.lolaf.staffix.admin.server.sessions;

/**
 * Mirrors staffix {@code FixSessionStatus}, which the engine works out; drives the session box colour.
 */
public enum SessionStatus {
    LOGGED_IN,
    LOGGED_OUT_BY_OPERATOR,
    LOGGED_OUT_OUTSIDE_SESSION_TIME,
    LOGGED_OUT_INSIDE_SESSION_TIME
}
