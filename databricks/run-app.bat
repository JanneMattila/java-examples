@ECHO OFF
SETLOCAL ENABLEDELAYEDEXPANSION

REM Ensure we execute from the directory containing this script
PUSHD "%~dp0"

REM Configure Java if desired (kept consistent with local run script)
IF NOT DEFINED JAVA_HOME (
    SET "JAVA_HOME=%LOCALAPPDATA%\Programs\Eclipse Adoptium\jdk-21.0.6.7-hotspot"
)
SET "PATH=%JAVA_HOME%\bin;%PATH%"
ECHO Using JAVA_HOME: %JAVA_HOME%

REM Open internal JDK modules required by Spark / Databricks Connect
SET "MAVEN_OPTS=--add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.lang.invoke=ALL-UNNAMED --add-opens=java.base/java.lang.reflect=ALL-UNNAMED --add-opens=java.base/java.io=ALL-UNNAMED --add-opens=java.base/java.net=ALL-UNNAMED --add-opens=java.base/java.nio=ALL-UNNAMED --add-opens=java.base/java.util=ALL-UNNAMED --add-opens=java.base/java.util.concurrent=ALL-UNNAMED --add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED --add-opens=java.base/sun.nio.ch=ALL-UNNAMED --add-opens=java.base/sun.nio.cs=ALL-UNNAMED --add-opens=java.base/sun.security.action=ALL-UNNAMED --add-opens=java.base/sun.util.calendar=ALL-UNNAMED --add-exports=java.base/sun.nio.ch=ALL-UNNAMED"

REM Default to the "databricks-connect" profile if none is provided already
IF NOT DEFINED DATABRICKS_CONFIG_PROFILE (
    SET "DATABRICKS_CONFIG_PROFILE=databricks-connect"
)
ECHO Using Databricks profile: %DATABRICKS_CONFIG_PROFILE%

REM Informational banner
ECHO.
ECHO ==================================================
ECHO Running Word Count via Databricks Connect
ECHO ==================================================
ECHO.

SET "MAIN_CLASS=com.example.spark.WordCountAppDatabricks"
SET "ARGS="
IF NOT "%~1"=="" (
    SET "ARGS=%~1"
)

IF "%ARGS%"=="" (
    ECHO No custom argument supplied; app will choose default remote/local input path.
    mvn exec:java -Dexec.mainClass=%MAIN_CLASS%
) ELSE (
    ECHO Using custom argument: %ARGS%
    mvn exec:java -Dexec.mainClass=%MAIN_CLASS% -Dexec.args="%ARGS%"
)

SET EXIT_CODE=%ERRORLEVEL%
POPD
ENDLOCAL & EXIT /B %EXIT_CODE%
