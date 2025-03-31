@ECHO OFF
SET JAVA_HOME=%LOCALAPPDATA%\Programs\Eclipse Adoptium\jdk-21.0.6.7-hotspot
SET PATH=%JAVA_HOME%\bin;%PATH%
ECHO Using JAVA_HOME: %JAVA_HOME%

REM These are placeholder values. Replace with your own values.
REM SET APP_CONFIGURATION_ENDPOINT=https://<youraccount>.azconfig.io/
REM SET APP_CONFIGURATION_CONNECTION_STRING=Endpoint=https://<youraccount>.azconfig.io;Id=<value>;Secret=<secret>
REM SET KEY_VAULT_ENDPOINT=https://<youraccount>.vault.azure.net/

ECHO APP_CONFIGURATION_ENDPOINT=%APP_CONFIGURATION_ENDPOINT%
ECHO APP_CONFIGURATION_CONNECTION_STRING=%APP_CONFIGURATION_CONNECTION_STRING%
ECHO KEY_VAULT_ENDPOINT=%KEY_VAULT_ENDPOINT%

mvn spring-boot:run
