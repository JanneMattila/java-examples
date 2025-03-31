package com.example.springcloudazure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.azure.data.appconfiguration.ConfigurationClient;
import com.azure.data.appconfiguration.ConfigurationClientBuilder;
import com.azure.identity.DefaultAzureCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;

@Configuration
public class AzureAppConfigConfig {

    private static final Logger logger = LoggerFactory.getLogger(AzureAppConfigConfig.class);

    @Value("${spring.cloud.azure.appconfiguration.endpoint:}")
    private String endpoint;
    
    @Value("${spring.cloud.azure.appconfiguration.stores[0].connection-string:}")
    private String connectionString;

    @Bean
    public ConfigurationClient configurationClient() {
        logger.info("Creating ConfigurationClient for Azure App Configuration");
        
        try {

            if (endpoint != null && !endpoint.isEmpty()) {
                logger.info("Using endpoint with DefaultAzureCredential to authenticate to App Configuration at: {}", endpoint);
                DefaultAzureCredential credential = new DefaultAzureCredentialBuilder().build();
                return new ConfigurationClientBuilder()
                    .endpoint(endpoint)
                    .credential(credential)
                    .buildClient();
            }

            if (connectionString != null && !connectionString.isEmpty()) {
                logger.info("Using connection string to authenticate to App Configuration");
                return new ConfigurationClientBuilder()
                    .connectionString(connectionString)
                    .buildClient();
            }
            
            logger.warn("No valid App Configuration endpoint or connection string found. ConfigurationClient will not be created.");
            throw new IllegalStateException("No valid Azure App Configuration connection details found");
        } catch (Exception e) {
            logger.error("Failed to create ConfigurationClient: {}", e.getMessage(), e);
            throw e;
        }
    }
}