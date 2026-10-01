package org.lolaf.staffix.admin.server.security;

/**
 * WRITER implies READER. ENGINE is for engines announcing themselves and grants nothing in the UI.
 */
public enum AdminRole {
    READER,
    WRITER,
    ENGINE;

    public String authority() {
        return "ROLE_" + name();
    }
}
