package com.example.springcloudazure.controller;

import com.example.springcloudazure.config.ConfigProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.*;
import java.util.stream.Collectors;

@Controller
public class ConfigController {
    private static final Logger logger = LoggerFactory.getLogger(ConfigController.class);

    @Autowired
    private ConfigProperties configProperties;
    
    @Autowired
    private Environment environment;

    @GetMapping("/")
    public String home(Model model) {
        // Create a map to store all configuration values by source
        Map<String, Map<String, String>> configBySource = new HashMap<>();
        
        // Add values from ConfigProperties class
        Map<String, String> configPropertiesValues = new HashMap<>();
        configPropertiesValues.put("message", configProperties.getMessage());
        configPropertiesValues.put("secretSetting", configProperties.getSecretSetting());
        configPropertiesValues.put("anotherSecretSetting", configProperties.getAnotherSecretSetting());
        configPropertiesValues.put("database", configProperties.getDatabase());
        configBySource.put("Config Properties", configPropertiesValues);
        
        // Extract and categorize all properties from the environment by source
        if (environment instanceof AbstractEnvironment) {
            AbstractEnvironment abstractEnv = (AbstractEnvironment) environment;
            
            // Track which properties we've already processed to avoid duplicates
            Set<String> processedKeys = new HashSet<>();
            
            // Process property sources in reverse to favor higher precedence sources first
            List<PropertySource<?>> propertySources = new ArrayList<>();
            abstractEnv.getPropertySources().forEach(propertySources::add);
            Collections.reverse(propertySources);
            
            for (PropertySource<?> propertySource : propertySources) {
                Map<String, String> sourceProperties = new HashMap<>();
                String sourceName = propertySource.getName();
                
                logger.info("Processing property source: {}", sourceName);
                
                if (shouldSkipPropertySource(sourceName)) {
                    logger.debug("Skipping property source: {}", sourceName);
                    continue;
                }
                
                // Only process property sources that allow enumeration of properties
                if (propertySource instanceof EnumerablePropertySource) {
                    EnumerablePropertySource<?> enumerableSource = (EnumerablePropertySource<?>) propertySource;
                    
                    // Group property sources by their type
                    String groupName = getPropertySourceGroupName(sourceName);
                    
                    // Skip if the group name is null (this means we want to ignore this source)
                    if (groupName == null) {
                        logger.debug("Skipping property source with null group name: {}", sourceName);
                        continue;
                    }
                    
                    logger.info("Processing property source: {} as group: {}", sourceName, groupName);
                    
                    for (String propertyName : enumerableSource.getPropertyNames()) {
                        // Skip if we've already processed this key or if it's not relevant
                        if (shouldSkipProperty(propertyName) || processedKeys.contains(propertyName)) {
                            continue;
                        }
                        
                        try {
                            Object value = propertySource.getProperty(propertyName);
                            if (value != null) {
                                // Mask sensitive values
                                String displayValue = shouldMaskValue(propertyName) ? "******" : value.toString();
                                sourceProperties.put(propertyName, displayValue);
                                processedKeys.add(propertyName);
                                logger.debug("Added property: {} = {} from source: {}", propertyName, displayValue, sourceName);
                            }
                        } catch (Exception e) {
                            logger.debug("Error getting property '{}' from source '{}': {}", 
                                      propertyName, sourceName, e.getMessage());
                        }
                    }
                    
                    // If we found properties in this source, add them to the result
                    if (!sourceProperties.isEmpty()) {
                        // Get existing map or create new one for this group
                        Map<String, String> existingMap = configBySource.getOrDefault(groupName, new HashMap<>());
                        existingMap.putAll(sourceProperties);
                        configBySource.put(groupName, existingMap);
                        logger.info("Added {} properties to group: {}", sourceProperties.size(), groupName);
                    } else {
                        logger.debug("No properties found in source: {}", sourceName);
                    }
                }
            }
        }
        
        // Sort the map entries for consistent display, filter out null keys
        Map<String, Map<String, String>> sortedConfigBySource = configBySource.entrySet().stream()
            .filter(entry -> entry.getKey() != null) // Filter out entries with null keys
            .sorted((e1, e2) -> {
                // Null-safe comparison
                if (e1.getKey() == null && e2.getKey() == null) return 0;
                if (e1.getKey() == null) return -1;
                if (e2.getKey() == null) return 1;
                return e1.getKey().compareTo(e2.getKey());
            })
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> sortMap(entry.getValue()),
                (e1, e2) -> e1,
                LinkedHashMap::new
            ));
        
        model.addAttribute("configBySource", sortedConfigBySource);
        return "config";
    }
    
    private boolean shouldSkipPropertySource(String sourceName) {
        // Skip internal Spring sources that aren't relevant for users
        return sourceName.contains("servletConfigInitParams") ||
               sourceName.contains("servletContextInitParams") ||
               sourceName.contains("systemProperties") ||
               sourceName.contains("systemEnvironment") ||
               sourceName.contains("server.ports") ||
               sourceName.contains("Bootstrap Properties") ||
               sourceName.contains("Application Properties") ||
               sourceName.contains("classpath") ||
               sourceName.contains("springCloudClientHostInfo");
    }
    
    private boolean shouldSkipProperty(String propertyName) {
        // Skip internal properties that aren't relevant
        return propertyName.startsWith("java.") ||
               propertyName.startsWith("sun.") ||
               propertyName.startsWith("user.") ||
               propertyName.startsWith("os.") ||
               propertyName.startsWith("spring.") ||
               propertyName.startsWith("PID");
    }
    
    private boolean shouldMaskValue(String propertyName) {
        // Mask sensitive values
        return false; // Change to true if you want to mask all values
        // return propertyName.toLowerCase().contains("password") ||
        //        propertyName.toLowerCase().contains("secret") ||
        //        propertyName.toLowerCase().contains("key") ||
        //        propertyName.toLowerCase().contains("token") ||
        //        propertyName.toLowerCase().contains("credentials") ||
        //        propertyName.toLowerCase().contains("connection-string");
    }
    
    private String getPropertySourceGroupName(String sourceName) {
        // Group property sources by type for more intuitive display
        if (sourceName.toLowerCase().contains("vault") || 
            sourceName.toLowerCase().contains("key-vault") ||
            sourceName.toLowerCase().contains("keyvault")) {
            logger.info("Found Key Vault property source: {}", sourceName);
            return "Azure Key Vault";
        } else if (sourceName.toLowerCase().contains("appconfig") || 
                  sourceName.toLowerCase().contains("app-config")) {
            return "Azure App Configuration";
        } else if (sourceName.contains("bootstrap")) {
            return "Bootstrap Properties";
        } else if (sourceName.contains("application")) {
            return "Application Properties";
        } else {
            return sourceName;
        }
    }
    
    private Map<String, String> sortMap(Map<String, String> map) {
        // Sort map entries by key with null-safe comparison
        return map.entrySet().stream()
            .filter(entry -> entry.getKey() != null) // Filter out entries with null keys
            .sorted((e1, e2) -> {
                // Null-safe comparison
                if (e1.getKey() == null && e2.getKey() == null) return 0;
                if (e1.getKey() == null) return -1;
                if (e2.getKey() == null) return 1;
                return e1.getKey().compareTo(e2.getKey());
            })
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (e1, e2) -> e1,
                LinkedHashMap::new
            ));
    }
}