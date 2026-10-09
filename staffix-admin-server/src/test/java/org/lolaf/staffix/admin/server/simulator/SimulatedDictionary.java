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
package org.lolaf.staffix.admin.server.simulator;

import org.lolaf.staffix.admin.server.dictionaries.DictionaryRef;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * staffix's FIX 4.4 dictionary, from the {@code staffix-fix-44} artifact, which every simulated session uses.
 */
class SimulatedDictionary {

    private static final String ID = "default-FIX.4.4";
    private static final String RESOURCE = "/FIX44.xml";

    private final byte[] xml = load();
    private final DictionaryRef ref = DictionaryRef.of(ID, sha256(xml));

    DictionaryRef ref() {
        return ref;
    }

    byte[] xml() {
        return xml;
    }

    private static byte[] load() {
        try (InputStream in = SimulatedDictionary.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(RESOURCE + " is missing from the classpath (staffix-fix-44)");
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
