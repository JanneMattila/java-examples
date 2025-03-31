package com.example.springcloudazure.controller;

import com.azure.security.keyvault.secrets.SecretClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Diagnostic endpoints for Azure Key Vault to help troubleshoot issues.
 */
@RestController
@RequestMapping("/api/diagnostics")
public class KeyVaultDiagnosticsController {

    private static final Logger logger = LoggerFactory.getLogger(KeyVaultDiagnosticsController.class);
    
    private final Environment environment;
    private final SecretClient secretClient;

    @Autowired
    public KeyVaultDiagnosticsController(Environment environment, 
                                        @Autowired(required = false) SecretClient secretClient) {
        this.environment = environment;
        this.secretClient = secretClient;
        logger.info("KeyVaultDiagnosticsController initialized with SecretClient: {}", 
                    secretClient != null ? "Available" : "Not Available");
    }

    @GetMapping("/key-vault")
    public ResponseEntity<Map<String, Object>> getKeyVaultDiagnostics() {
        Map<String, Object> diagnostics = new HashMap<>();
        
        // Client status
        diagnostics.put("clientAvailable", secretClient != null);
        
        if (secretClient != null) {
            try {
                diagnostics.put("vaultUrl", secretClient.getVaultUrl());
                
                // Direct check from Azure Key Vault service
                List<Map<String, String>> keyVaultSecrets = new ArrayList<>();
                
                secretClient.listPropertiesOfSecrets().forEach(secretProperties -> {
                    Map<String, String> secretMap = new HashMap<>();
                    secretMap.put("name", secretProperties.getName());
                    secretMap.put("enabled", String.valueOf(secretProperties.isEnabled()));
                    secretMap.put("contentType", secretProperties.getContentType());
                    
                    // Don't fetch the actual secret values for security reasons
                    // Just indicate if they can be accessed
                    try {
                        boolean exists = secretClient.getSecret(secretProperties.getName()) != null;
                        secretMap.put("accessible", String.valueOf(exists));
                    } catch (Exception e) {
                        secretMap.put("accessible", "false - " + e.getMessage());
                    }
                    
                    keyVaultSecrets.add(secretMap);
                });
                
                diagnostics.put("secretsInKeyVault", keyVaultSecrets);
                diagnostics.put("secretCount", keyVaultSecrets.size());
            } catch (Exception e) {
                diagnostics.put("error", e.getMessage());
                logger.error("Error accessing Key Vault", e);
            }
        }
        
        // Check if Key Vault properties are available in the Spring Environment
        Map<String, String> keyVaultPropertiesInEnvironment = new HashMap<>();
        
        // Check for specific keys
        String[] keysToCheck = {
            "mysecret",
            "connection-string",
            "config.secretValue",
            "config.databaseUrl",
            "config.from-key-vault"
        };
        
        for (String key : keysToCheck) {
            String value = environment.getProperty(key);
            // Mask actual values for security
            keyVaultPropertiesInEnvironment.put(key, value != null ? "Value exists (masked)" : "Not found");
        }
        
        diagnostics.put("keyVaultPropertiesInEnvironment", keyVaultPropertiesInEnvironment);
        
        // Find Key Vault property sources
        List<String> keyVaultSources = new ArrayList<>();
        if (environment instanceof AbstractEnvironment) {
            for (PropertySource<?> propertySource : ((AbstractEnvironment) environment).getPropertySources()) {
                if (propertySource.getName().toLowerCase().contains("vault") || 
                    propertySource.getClass().getName().toLowerCase().contains("vault")) {
                    keyVaultSources.add(propertySource.getName() + " (" + propertySource.getClass().getSimpleName() + ")");
                }
            }
        }
        diagnostics.put("keyVaultPropertySources", keyVaultSources);
        
        return ResponseEntity.ok(diagnostics);
    }
}