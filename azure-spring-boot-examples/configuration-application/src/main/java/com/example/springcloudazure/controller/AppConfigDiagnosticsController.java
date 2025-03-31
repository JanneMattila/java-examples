package com.example.springcloudazure.controller;

import com.azure.data.appconfiguration.ConfigurationClient;
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
 * Diagnostic endpoints for Azure App Configuration to help troubleshoot issues.
 */
@RestController
@RequestMapping("/api/diagnostics")
public class AppConfigDiagnosticsController {

    private static final Logger logger = LoggerFactory.getLogger(AppConfigDiagnosticsController.class);
    
    private final Environment environment;
    private final ConfigurationClient configurationClient;

    @Autowired
    public AppConfigDiagnosticsController(Environment environment, 
                                          @Autowired(required = false) ConfigurationClient configurationClient) {
        this.environment = environment;
        this.configurationClient = configurationClient;
        logger.info("AppConfigDiagnosticsController initialized with ConfigurationClient: {}", 
                    configurationClient != null ? "Available" : "Not Available");
    }

    @GetMapping("/app-config")
    public ResponseEntity<Map<String, Object>> getAppConfigurationDiagnostics() {
        Map<String, Object> diagnostics = new HashMap<>();
        
        // Client status
        diagnostics.put("clientAvailable", configurationClient != null);
        
        if (configurationClient != null) {
            try {
                diagnostics.put("endpoint", configurationClient.getEndpoint());
                
                // Direct check from Azure App Configuration service
                List<Map<String, String>> appConfigSettings = new ArrayList<>();
                
                configurationClient.listConfigurationSettings(null).forEach(setting -> {
                    Map<String, String> settingMap = new HashMap<>();
                    settingMap.put("key", setting.getKey());
                    settingMap.put("value", setting.getValue());
                    settingMap.put("label", setting.getLabel() == null ? "(no label)" : setting.getLabel());
                    settingMap.put("contentType", setting.getContentType());
                    appConfigSettings.add(settingMap);
                });
                
                diagnostics.put("directSettingsFromService", appConfigSettings);
                diagnostics.put("directSettingsCount", appConfigSettings.size());
            } catch (Exception e) {
                diagnostics.put("error", e.getMessage());
                logger.error("Error accessing App Configuration service", e);
            }
        }

        // List all property sources to see if App Configuration source exists
        List<String> propertySources = new ArrayList<>();
        if (environment instanceof AbstractEnvironment) {
            for (PropertySource<?> propertySource : ((AbstractEnvironment) environment).getPropertySources()) {
                propertySources.add(propertySource.getName() + " (" + propertySource.getClass().getSimpleName() + ")");
            }
        }
        diagnostics.put("propertySources", propertySources);
        
        return ResponseEntity.ok(diagnostics);
    }
}