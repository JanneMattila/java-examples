package com.example.springcloudazure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.stereotype.Component;

/**
 * This environment post processor ensures App Configuration keys with slashes
 * are also accessible with dot notation, which is more common in Spring applications.
 */
@Component
public class AppConfigPropertyProcessor implements EnvironmentPostProcessor {
    
    private static final Logger logger = LoggerFactory.getLogger(AppConfigPropertyProcessor.class);
    
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        logger.info("Processing environment properties for App Configuration keys with slashes");
        
        MutablePropertySources propertySources = environment.getPropertySources();
        
        // For each property source
        for (PropertySource<?> propertySource : propertySources) {
            // If it's an enumerable property source (which most are)
            if (propertySource instanceof EnumerablePropertySource) {
                EnumerablePropertySource<?> enumerablePropertySource = (EnumerablePropertySource<?>) propertySource;
                
                for (String propertyName : enumerablePropertySource.getPropertyNames()) {
                    // If the property name starts with /application/
                    if (propertyName != null && propertyName.startsWith("/application/")) {
                        Object value = propertySource.getProperty(propertyName);
                        String normalizedName = propertyName.replace("/", ".");
                        
                        // Add a new property with dots instead of slashes
                        if (normalizedName.startsWith(".")) {
                            normalizedName = normalizedName.substring(1);
                        }
                        
                        logger.debug("Adding normalized property: {} -> {} = {}", propertyName, normalizedName, value);
                        
                        // We can't modify the original property sources directly,
                        // but environment.getSystemProperties() is a mutable map we can use
                        System.setProperty(normalizedName, value.toString());
                    }
                }
            }
        }
    }
}