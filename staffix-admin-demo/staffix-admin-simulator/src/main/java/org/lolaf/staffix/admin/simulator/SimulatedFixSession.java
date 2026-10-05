package org.lolaf.staffix.admin.simulator;

import lombok.experimental.UtilityClass;
import org.lolaf.staffix.api.FixDictionaryId;
import org.lolaf.staffix.api.application.FixApplication;
import org.lolaf.staffix.api.session.FixSession;
import org.lolaf.staffix.api.version.FixRegularVersion;

import java.lang.reflect.Proxy;

/**
 * A running config seen as the {@link FixSession} staffix's HTTP connector reads its state from. It answers what the
 * connector asks; anything else, such as sending through the session, is not simulated.
 */
@UtilityClass
class SimulatedFixSession {

    /**
     * Every simulated session speaks FIX 4.4's standard dictionary, the one the simulator ships.
     */
    private static final FixApplication FIX_44_APPLICATION = (FixApplication) Proxy.newProxyInstance(FixApplication.class.getClassLoader(),
            new Class<?>[]{FixApplication.class}, (proxy, method, args) -> switch (method.getName()) {
                case "getDictionaryId" -> FixDictionaryId.of(FixRegularVersion.VERSION_44);
                case "toString" -> "simulated FIX 4.4 application";
                default -> throw new UnsupportedOperationException(method.getName() + " is not simulated");
            });

    /**
     * @param lock what the session's state is changed under
     */
    FixSession of(SimulatedSession session, Object lock) {
        return (FixSession) Proxy.newProxyInstance(FixSession.class.getClassLoader(), new Class<?>[]{FixSession.class}, (proxy, method, args) -> {
            synchronized (lock) {
                return switch (method.getName()) {
                    case "getFixSessionId" -> session.fixSessionId();
                    case "getFixSessionSettings" -> session.settings;
                    case "getApplication" -> FIX_44_APPLICATION;
                    case "isLoggedIn", "isConnected" -> session.loggedIn;
                    case "isWithinSessionTime" -> session.withinSessionTime;
                    case "getDesiredState" -> session.desiredState;
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "simulated " + session.fixSessionId();
                    default -> throw new UnsupportedOperationException(method.getName() + " is not simulated");
                };
            }
        });
    }
}
