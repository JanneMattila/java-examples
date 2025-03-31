package com.example.springcloudazure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import com.azure.data.appconfiguration.ConfigurationClient;
import com.azure.data.appconfiguration.models.ConfigurationSetting;

@Component
public class AppConfigHealthIndicator implements HealthIndicator {

    private static final Logger logger = LoggerFactory.getLogger(AppConfigHealthIndicator.class);
    private final ConfigurationClient configurationClient;

    public AppConfigHealthIndicator(ConfigurationClient configurationClient) {
        this.configurationClient = configurationClient;
        logger.info("App Configuration Health Indicator initialized with configuration client: {}", 
                  configurationClient != null ? "Available" : "Not Available");
    }

    @Override
    public Health health() {
        
        try {
            // Try to list settings to verify connectivity
            Iterable<ConfigurationSetting> settings = configurationClient.listConfigurationSettings(null);
            boolean hasSettings = settings.iterator().hasNext();
            
            if (hasSettings) {
                ConfigurationSetting firstSetting = settings.iterator().next();
                logger.info("Successfully accessed App Configuration. Found setting: {}", firstSetting.getKey());
            } else {
                logger.info("Successfully accessed App Configuration. No settings found.");
            }
            
            return Health.up()
                    .withDetail("status", "App Configuration connection successful")
                    .withDetail("endpoint", configurationClient.getEndpoint())
                    .withDetail("hasSettings", hasSettings)
                    .build();
        } catch (Exception e) {
            logger.error("Failed to connect to App Configuration: {}", e.getMessage(), e);
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .withDetail("endpoint", configurationClient.getEndpoint())
                    .build();
        }
    }
}