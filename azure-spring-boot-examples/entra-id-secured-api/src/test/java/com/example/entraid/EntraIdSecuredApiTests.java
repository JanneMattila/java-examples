package com.example.entraid;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "TENANT_ID=" + EntraIdSecuredApiTests.TENANT_ID,
        "API_CLIENT_ID=" + EntraIdSecuredApiTests.API_CLIENT_ID,
        "API_APP_ID_URI=api://" + EntraIdSecuredApiTests.API_CLIENT_ID,
        "ALLOWED_CLIENT_IDS=" + EntraIdSecuredApiTests.ALLOWED_CLIENT_ID + ", 99999999-9999-9999-9999-999999999999"
})
@AutoConfigureMockMvc
class EntraIdSecuredApiTests {

    static final String TENANT_ID = "11111111-1111-1111-1111-111111111111";
    static final String API_CLIENT_ID = "22222222-2222-2222-2222-222222222222";
    static final String ALLOWED_CLIENT_ID = "33333333-3333-3333-3333-333333333333";
    static final String NOT_ALLOWED_CLIENT_ID = "44444444-4444-4444-4444-444444444444";
    static final String ISSUER = "https://login.microsoftonline.com/" + TENANT_ID + "/v2.0";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private OAuth2TokenValidator<Jwt> entraIdTokenValidator;

    /**
     * Replaces network based signature validation with fixed test tokens.
     * Authorization rules and claim mappings are the real ones.
     */
    @TestConfiguration
    static class TestJwtDecoderConfiguration {
        @Bean
        @Primary
        JwtDecoder testJwtDecoder() {
            return token -> {
                switch (token) {
                    case "user-token": return jwt(ALLOWED_CLIENT_ID).claim("scp", "Echo.ReadWrite").build();
                    case "app-token": return jwt(ALLOWED_CLIENT_ID).claim("roles", Collections.singletonList("Echo.ReadWrite")).build();
                    case "no-scope-token": return jwt(ALLOWED_CLIENT_ID).build();
                    case "not-allowed-client-token": return jwt(NOT_ALLOWED_CLIENT_ID).claim("roles", Collections.singletonList("Echo.ReadWrite")).build();
                    default: throw new BadJwtException("Invalid token");
                }
            };
        }
    }

    static Jwt.Builder jwt(String clientId) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuer(ISSUER)
                .audience(Collections.singletonList(API_CLIENT_ID))
                .subject("subject-" + clientId)
                .claim("azp", clientId)
                .claim("tid", TENANT_ID)
                .issuedAt(Instant.now().minusSeconds(60))
                .notBefore(Instant.now().minusSeconds(60))
                .expiresAt(Instant.now().plusSeconds(3600));
    }

    private static final String BODY = "{\"hello\":\"world\"}";

    @Test
    void noTokenReturns401() throws Exception {
        mvc.perform(post("/api/echo").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidTokenReturns401() throws Exception {
        mvc.perform(post("/api/echo").header("Authorization", "Bearer garbage")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void delegatedScopeFromAllowedClientReturns200() throws Exception {
        mvc.perform(post("/api/echo").header("Authorization", "Bearer user-token")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("subject-" + ALLOWED_CLIENT_ID))
                .andExpect(jsonPath("$.authorities", hasItems("SCOPE_Echo.ReadWrite", "CLIENT_" + ALLOWED_CLIENT_ID)))
                .andExpect(jsonPath("$.claims.azp").value(ALLOWED_CLIENT_ID))
                .andExpect(jsonPath("$.request.hello").value("world"));
    }

    @Test
    void appRoleFromAllowedClientReturns200() throws Exception {
        mvc.perform(post("/api/echo").header("Authorization", "Bearer app-token")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorities", hasItems("APPROLE_Echo.ReadWrite", "CLIENT_" + ALLOWED_CLIENT_ID)));
    }

    @Test
    void missingScopeReturns403() throws Exception {
        mvc.perform(post("/api/echo").header("Authorization", "Bearer no-scope-token")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void notAllowListedClientReturns403() throws Exception {
        mvc.perform(post("/api/echo").header("Authorization", "Bearer not-allowed-client-token")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void tokenValidatorAcceptsValidToken() {
        assertThat(entraIdTokenValidator.validate(jwt(ALLOWED_CLIENT_ID).build()).hasErrors()).isFalse();
    }

    @Test
    void tokenValidatorAcceptsAppIdUriAudience() {
        Jwt token = jwt(ALLOWED_CLIENT_ID).audience(Collections.singletonList("api://" + API_CLIENT_ID)).build();
        assertThat(entraIdTokenValidator.validate(token).hasErrors()).isFalse();
    }

    @Test
    void tokenValidatorRejectsOtherTenant() {
        Jwt token = jwt(ALLOWED_CLIENT_ID).issuer("https://login.microsoftonline.com/55555555-5555-5555-5555-555555555555/v2.0").build();
        assertThat(entraIdTokenValidator.validate(token).hasErrors()).isTrue();
    }

    @Test
    void tokenValidatorRejectsOtherAudience() {
        Jwt token = jwt(ALLOWED_CLIENT_ID).audience(Collections.singletonList("https://graph.microsoft.com")).build();
        assertThat(entraIdTokenValidator.validate(token).hasErrors()).isTrue();
    }

    @Test
    void tokenValidatorRejectsExpiredToken() {
        Jwt token = jwt(ALLOWED_CLIENT_ID)
                .issuedAt(Instant.now().minusSeconds(7200))
                .notBefore(Instant.now().minusSeconds(7200))
                .expiresAt(Instant.now().minusSeconds(3600))
                .build();
        assertThat(entraIdTokenValidator.validate(token).hasErrors()).isTrue();
    }
}
