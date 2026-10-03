package org.lolaf.staffix.admin.simulator;

import lombok.experimental.UtilityClass;
import org.lolaf.staffix.api.session.FixSession;

import java.lang.reflect.Proxy;

/**
 * A running config seen as the {@link FixSession} staffix's HTTP connector reads its state from. It answers what the
 * connector asks; anything else, such as sending through the session, is not simulated.
 */
@UtilityClass
class SimulatedFixSession {

    /**
     * @param lock what the session's state is changed under
     */
    FixSession of(SimulatedSession session, Object lock) {
        return (FixSession) Proxy.newProxyInstance(FixSession.class.getClassLoader(), new Class<?>[]{FixSession.class}, (proxy, method, args) -> {
            synchronized (lock) {
                return switch (method.getName()) {
                    case "getFixSessionId" -> session.fixSessionId();
                    case "getFixSessionSettings" -> session.settings;
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
