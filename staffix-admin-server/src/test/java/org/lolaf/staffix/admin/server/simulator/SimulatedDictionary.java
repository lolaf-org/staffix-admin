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
