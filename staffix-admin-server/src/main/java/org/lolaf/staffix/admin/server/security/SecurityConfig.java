package org.lolaf.staffix.admin.server.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.authentication.jaas.AuthorityGranter;
import org.springframework.security.authentication.jaas.DefaultJaasAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;

import javax.security.auth.login.Configuration.Parameters;
import java.io.IOException;
import java.net.URI;
import java.security.NoSuchAlgorithmException;
import java.security.URIParameter;

/**
 * Operators log in through the SPA's own page (session cookie, CSRF token in a cookie); engines announce with HTTP
 * Basic. Both authenticate against the same JAAS realm.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    public static final String ANNOUNCE_PATH = "/api/engines/announce";

    @Bean
    @Order(1)
    SecurityFilterChain engineChain(HttpSecurity http) {
        return http.securityMatcher(ANNOUNCE_PATH)
                .authorizeHttpRequests(requests -> requests.anyRequest().hasRole(AdminRole.ENGINE.name()))
                .httpBasic(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable())
                .build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain operatorChain(HttpSecurity http) {
        return http
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/api/**").hasRole(AdminRole.READER.name())
                        .anyRequest().permitAll())
                .formLogin(login -> login
                        .loginPage("/login")
                        .loginProcessingUrl("/api/login")
                        .successHandler((request, response, authentication) -> response.setStatus(HttpStatus.NO_CONTENT.value()))
                        .failureHandler((request, response, exception) -> response.sendError(HttpStatus.UNAUTHORIZED.value(), "Bad credentials")))
                .logout(logout -> logout
                        .logoutUrl("/api/logout")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .csrf(csrf -> csrf.spa())
                .build();
    }

    @Bean
    static RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.withDefaultRolePrefix()
                .role(AdminRole.WRITER.name()).implies(AdminRole.READER.name())
                .build();
    }

    @Bean
    DefaultJaasAuthenticationProvider jaasAuthenticationProvider(SecurityProperties properties) throws IOException {
        DefaultJaasAuthenticationProvider provider = new DefaultJaasAuthenticationProvider();
        provider.setConfiguration(jaasConfiguration(properties));
        provider.setLoginContextName(properties.getLoginContextName());
        provider.setAuthorityGranters(new AuthorityGranter[]{new RoleAuthorityGranter(properties.getRoleMappings())});
        return provider;
    }

    private static javax.security.auth.login.Configuration jaasConfiguration(SecurityProperties properties) throws IOException {
        if (properties.getJaasConfig() == null) {
            return javax.security.auth.login.Configuration.getConfiguration();
        }
        URI location = properties.getJaasConfig().getURI();
        Parameters parameters = new URIParameter(location);
        try {
            return javax.security.auth.login.Configuration.getInstance("JavaLoginConfig", parameters);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Cannot read the JAAS configuration " + location, e);
        }
    }
}
