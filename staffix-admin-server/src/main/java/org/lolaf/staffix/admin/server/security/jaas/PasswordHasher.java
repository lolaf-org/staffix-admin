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
