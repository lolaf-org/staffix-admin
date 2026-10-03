package org.lolaf.staffix.admin.connector;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Explains a connector setting to the admin editing it, in the console's form; javadoc is not available at runtime.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Description {

    /**
     * A sentence or two in plain text: what the setting changes and when to change it.
     */
    String value();
}
