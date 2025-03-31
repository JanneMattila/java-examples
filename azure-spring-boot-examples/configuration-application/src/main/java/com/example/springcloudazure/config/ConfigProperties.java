package com.example.springcloudazure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "config")
public class ConfigProperties {
    
    private String message;
    private String database;
    private String secretSetting;
    private String anotherSecretSetting;
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public String getDatabase() {
        return database;
    }
    
    public void setDatabase(String database) {
        this.database = database;
    }
    
    public String getSecretSetting() {
        return secretSetting;
    }
    
    public void setSecretSetting(String secretSetting) {
        this.secretSetting = secretSetting;
    }

    public String getAnotherSecretSetting() {
        return anotherSecretSetting;
    }

    public void setAnotherSecretSetting(String anotherSecretSetting) {
        this.anotherSecretSetting = anotherSecretSetting;
    }
}