package com.example.entraid;

import com.azure.spring.cloud.autoconfigure.aad.AadResourceServerWebSecurityConfigurerAdapter;
import com.azure.spring.cloud.autoconfigure.aad.properties.AadAuthenticationProperties;
import com.azure.spring.cloud.autoconfigure.aad.properties.AadAuthorizationServerEndpoints;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.util.Arrays;
import java.util.List;

/**
 * Three layers of validation:
 * <ol>
 *   <li>Standard Entra ID token validation (signature, issuer, audience, lifetime) in {@link #jwtDecoder}. Failure: 401.</li>
 *   <li>Scope / app role validation using {@code @PreAuthorize} in {@link EchoController}. Failure: 403.</li>
 *   <li>Allow-listed clients: "azp" claim is mapped to "CLIENT_" authority (see application.yml)
 *       and must be in {@code app.allowed-client-ids}. Failure: 403.</li>
 * </ol>
 */
@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class SecurityConfig extends AadResourceServerWebSecurityConfigurerAdapter {

    static final String CLIENT_AUTHORITY_PREFIX = "CLIENT_";

    @Value("${app.allowed-client-ids}")
    private String[] allowedClientIds;

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        // Microsoft Entra ID resource server setup (JWT bearer + claim to authority mapping)
        super.configure(http);

        String[] allowedClients = Arrays.stream(allowedClientIds)
                .map(clientId -> CLIENT_AUTHORITY_PREFIX + clientId)
                .toArray(String[]::new);

        // Stateless bearer token API: no cookies, so no CSRF protection needed
        http.csrf().disable()
            .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            .and()
            .authorizeRequests()
                .antMatchers("/api/**").hasAnyAuthority(allowedClients)
                .anyRequest().denyAll();
    }

    @Bean
    public JwtDecoder jwtDecoder(AadAuthenticationProperties properties, OAuth2TokenValidator<Jwt> entraIdTokenValidator) {
        AadAuthorizationServerEndpoints endpoints = endpoints(properties);
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(endpoints.getJwkSetEndpoint()).build();
        decoder.setJwtValidator(entraIdTokenValidator);
        return decoder;
    }

    @Bean
    public OAuth2TokenValidator<Jwt> entraIdTokenValidator(AadAuthenticationProperties properties) {
        // v2.0 access token issuer of our own tenant (exact match, other tenants are rejected)
        String issuer = endpoints(properties).getBaseUri() + properties.getProfile().getTenantId() + "/v2.0";
        List<String> validAudiences = Arrays.asList(
                properties.getCredential().getClientId(),
                properties.getAppIdUri());

        return new DelegatingOAuth2TokenValidator<>(
                // Validates "iss", "exp" and "nbf"
                JwtValidators.createDefaultWithIssuer(issuer),
                new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                        aud -> aud != null && aud.stream().anyMatch(validAudiences::contains)));
    }

    private static AadAuthorizationServerEndpoints endpoints(AadAuthenticationProperties properties) {
        return new AadAuthorizationServerEndpoints(
                properties.getProfile().getEnvironment().getActiveDirectoryEndpoint(),
                properties.getProfile().getTenantId());
    }
}
