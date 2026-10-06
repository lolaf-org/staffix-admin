package org.lolaf.staffix.admin.server.security.jaas;

import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.FailedLoginException;
import javax.security.auth.login.LoginException;
import javax.security.auth.spi.LoginModule;
import java.io.IOException;
import java.io.InputStream;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Users from a properties file, for development and demos; production plugs in its own JAAS module (LDAP, ...).
 *
 * <p>Option {@code users} is a Spring resource location. Each line is {@code name = password, ROLE[, ROLE...]}, the
 * password in Spring's {@code {id}encoded} form ({@code {bcrypt}...}, or {@code {noop}...} in development). The file
 * is read on every login, so edits apply without a restart.
 */
public class PropertiesFileLoginModule implements LoginModule {

    private static final PasswordEncoder PASSWORD_ENCODER = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    private Subject subject;
    private CallbackHandler callbackHandler;
    private String usersLocation;
    private List<Principal> principals = List.of();

    @Override
    public void initialize(Subject subject, CallbackHandler callbackHandler, Map<String, ?> sharedState,
                           Map<String, ?> options) {
        this.subject = subject;
        this.callbackHandler = callbackHandler;
        this.usersLocation = (String) options.get("users");
    }

    @Override
    public boolean login() throws LoginException {
        NameCallback name = new NameCallback("username");
        PasswordCallback password = new PasswordCallback("password", false);
        try {
            callbackHandler.handle(new Callback[]{name, password});
        } catch (IOException | UnsupportedCallbackException e) {
            throw loginException("Cannot read the credentials", e);
        }
        String user = name.getName();
        String entry = loadUsers().getProperty(user == null ? "" : user);
        if (entry == null || password.getPassword() == null) {
            throw new FailedLoginException("Bad credentials");
        }
        List<String> passwordAndRoles = Arrays.stream(entry.split(",")).map(String::trim).toList();
        if (!PASSWORD_ENCODER.matches(new String(password.getPassword()), passwordAndRoles.getFirst())) {
            throw new FailedLoginException("Bad credentials");
        }
        password.clearPassword();
        List<Principal> granted = new ArrayList<>();
        granted.add(new UserPrincipal(user));
        passwordAndRoles.stream().skip(1).map(RolePrincipal::new).forEach(granted::add);
        principals = granted;
        return true;
    }

    @Override
    public boolean commit() {
        subject.getPrincipals().addAll(principals);
        return true;
    }

    @Override
    public boolean abort() {
        principals = List.of();
        return true;
    }

    @Override
    public boolean logout() {
        principals.forEach(subject.getPrincipals()::remove);
        principals = List.of();
        return true;
    }

    private Properties loadUsers() throws LoginException {
        if (usersLocation == null) {
            throw new LoginException("Option 'users' is missing from the JAAS configuration");
        }
        Resource resource = new DefaultResourceLoader(PropertiesFileLoginModule.class.getClassLoader()).getResource(usersLocation);
        Properties users = new Properties();
        try (InputStream in = resource.getInputStream()) {
            users.load(in);
        } catch (IOException e) {
            throw loginException("Cannot read users from " + usersLocation, e);
        }
        return users;
    }

    private static LoginException loginException(String message, Exception cause) {
        LoginException exception = new LoginException(message);
        exception.initCause(cause);
        return exception;
    }
}
