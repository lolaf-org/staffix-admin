package org.lolaf.staffix.admin.server.dictionaries;

import lombok.Value;

/**
 * A dictionary as an engine names it, with a hash of its content: the cache key, so engines sharing a dictionary
 * share one parsed copy and a changed dictionary is never served stale.
 */
@Value(staticConstructor = "of")
public class DictionaryRef {
    String id;
    String hash;
}
