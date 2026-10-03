package org.lolaf.staffix.admin.server.security.jaas;

import org.springframework.security.crypto.factory.PasswordEncoderFactories;

/**
 * Prints a password in the form {@link PropertiesFileLoginModule}'s users file expects, so no other tool is needed:
 * {@code java -Dloader.main=org.lolaf.staffix.admin.server.security.jaas.PasswordHasher -jar staffix-admin.jar <password>}.
 */
public final class PasswordHasher {

    private PasswordHasher() {
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: PasswordHasher <password>");
            System.exit(2);
        }
        System.out.println(PasswordEncoderFactories.createDelegatingPasswordEncoder().encode(args[0]));
    }
}
