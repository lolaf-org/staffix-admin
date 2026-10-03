package org.lolaf.staffix.admin.secrets;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a connector setting holding a credential. The console never returns, logs or audits its value: it is stored
 * as a reference a {@link SecretResolver} resolves, or encrypted under the console's master key.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Secret {
}
