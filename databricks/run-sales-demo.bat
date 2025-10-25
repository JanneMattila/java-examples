@echo off
REM Run the Sales Analytics Demo

REM Check if JAVA_HOME is set
if "%JAVA_HOME%"=="" (
    echo Error: JAVA_HOME is not set.
    echo Please set JAVA_HOME to your JDK installation directory.
    exit /b 1
)

echo Using JAVA_HOME: %JAVA_HOME%

REM Check for Databricks profile (optional)
if not "%DATABRICKS_PROFILE%"=="" (
    echo Using Databricks profile: %DATABRICKS_PROFILE%
) else (
    set DATABRICKS_PROFILE=databricks-connect
    echo Using Databricks profile: %DATABRICKS_PROFILE%
)

echo.
echo ==================================================
echo Running Sales Analytics Demo
echo ==================================================
echo.

REM Check if file path argument is provided
if "%1"=="" (
    echo No custom argument supplied; app will choose default local/remote path.
) else (
    echo Using custom file path: %1
)

REM Run Maven with the SalesAnalyticsDemo main class
REM Add Java module options for Arrow memory access
set MAVEN_OPTS=--add-opens=java.base/java.nio=ALL-UNNAMED
mvn compile exec:java -Dexec.mainClass="com.example.databricks.SalesAnalyticsDemo" -Dexec.args="%*"
