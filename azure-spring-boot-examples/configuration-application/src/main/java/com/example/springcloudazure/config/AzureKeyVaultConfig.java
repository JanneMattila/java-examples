package com.example.springcloudazure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.azure.identity.DefaultAzureCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;

@Configuration
public class AzureKeyVaultConfig {

    private static final Logger logger = LoggerFactory.getLogger(AzureKeyVaultConfig.class);

    @Value("${spring.cloud.azure.keyvault.secret.property-sources[0].endpoint}")
    private String vaultUri;

    @Bean
    public SecretClient secretClient() {
        logger.info("Creating SecretClient for Azure Key Vault with URI: {}", vaultUri);
        
        try {
            if (vaultUri == null || vaultUri.isEmpty() || !vaultUri.contains(".vault.azure.net")) {
                logger.error("Invalid Key Vault URI: {}", vaultUri);
                throw new IllegalArgumentException("Invalid Key Vault URI provided: " + vaultUri);
            }
            
            DefaultAzureCredential credential = new DefaultAzureCredentialBuilder()
                .build();
            
            SecretClient client = new SecretClientBuilder()
                .vaultUrl(vaultUri)
                .credential(credential)
                .buildClient();
            
            // Test the client by making a simple API call
            logger.info("Testing Key Vault connection...");
            client.listPropertiesOfSecrets().stream().limit(1).forEach(secret -> 
                logger.info("Successfully connected to Key Vault. Found secret: {}", secret.getName())
            );
            
            return client;
        } catch (Exception e) {
            logger.error("Failed to create or test SecretClient: {}", e.getMessage(), e);
            throw e;
        }
    }
}