package com.example.springcloudazure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import com.azure.security.keyvault.secrets.SecretClient;

@Component
public class KeyVaultHealthIndicator implements HealthIndicator {

    private static final Logger logger = LoggerFactory.getLogger(KeyVaultHealthIndicator.class);
    private final SecretClient secretClient;

    public KeyVaultHealthIndicator(SecretClient secretClient) {
        this.secretClient = secretClient;
        logger.info("Key Vault Health Indicator initialized with secret client: {}", 
                    secretClient != null ? "Available" : "Not Available");
    }

    @Override
    public Health health() {
        try {
            // Try to list secrets to verify connectivity
            secretClient.listPropertiesOfSecrets().stream().limit(1).forEach(secretProperties -> {
                logger.info("Successfully accessed Key Vault. Found secret: {}", secretProperties.getName());
            });
            
            return Health.up()
                    .withDetail("status", "Key Vault connection successful")
                    .withDetail("vaultUrl", secretClient.getVaultUrl())
                    .build();
        } catch (Exception e) {
            logger.error("Failed to connect to Key Vault: {}", e.getMessage(), e);
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .withDetail("vaultUrl", secretClient.getVaultUrl())
                    .build();
        }
    }
}