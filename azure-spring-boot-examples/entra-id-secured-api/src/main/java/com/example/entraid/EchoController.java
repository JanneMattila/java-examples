package com.example.entraid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class EchoController {

    /**
     * Requires either delegated permission (scope "scp" claim) or
     * application permission (app role "roles" claim) named "Echo.ReadWrite".
     */
    @PostMapping("/echo")
    @PreAuthorize("hasAnyAuthority('SCOPE_Echo.ReadWrite', 'APPROLE_Echo.ReadWrite')")
    public Map<String, Object> echo(@RequestBody(required = false) Map<String, Object> body,
                                    JwtAuthenticationToken authentication) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "Hello from Java " + System.getProperty("java.version"));
        response.put("name", authentication.getName());
        response.put("authorities", authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .sorted()
                .collect(Collectors.toList()));
        response.put("claims", authentication.getToken().getClaims());
        response.put("request", body);
        return response;
    }
}
