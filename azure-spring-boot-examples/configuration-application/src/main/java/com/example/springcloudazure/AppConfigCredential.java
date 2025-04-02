package com.example.springcloudazure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.azure.data.appconfiguration.ConfigurationClientBuilder;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.spring.cloud.appconfiguration.config.ConfigurationClientCustomizer;

@Component
public class AppConfigCredential implements ConfigurationClientCustomizer {

private static final Logger logger = LoggerFactory.getLogger(AppConfigCredential.class);

    @Override
    public void customize(ConfigurationClientBuilder builder, String endpoint) {

        logger.info("Customizing ConfigurationClientBuilder with endpoint: {}", endpoint);

        builder.credential(new DefaultAzureCredentialBuilder().build());
    }
}