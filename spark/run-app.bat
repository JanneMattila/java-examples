@ECHO OFF
SET JAVA_HOME=%LOCALAPPDATA%\Programs\Eclipse Adoptium\jdk-21.0.6.7-hotspot
SET PATH=%JAVA_HOME%\bin;%PATH%
ECHO Using JAVA_HOME: %JAVA_HOME%

REM Set JVM arguments for Java 17+ compatibility with Apache Spark
REM These options open up internal Java modules that Spark needs to access
SET MAVEN_OPTS=--add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.lang.invoke=ALL-UNNAMED --add-opens=java.base/java.lang.reflect=ALL-UNNAMED --add-opens=java.base/java.io=ALL-UNNAMED --add-opens=java.base/java.net=ALL-UNNAMED --add-opens=java.base/java.nio=ALL-UNNAMED --add-opens=java.base/java.util=ALL-UNNAMED --add-opens=java.base/java.util.concurrent=ALL-UNNAMED --add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED --add-opens=java.base/sun.nio.ch=ALL-UNNAMED --add-opens=java.base/sun.nio.cs=ALL-UNNAMED --add-opens=java.base/sun.security.action=ALL-UNNAMED --add-opens=java.base/sun.util.calendar=ALL-UNNAMED --add-exports=java.base/sun.nio.ch=ALL-UNNAMED

REM Run the Apache Spark local example
ECHO.
ECHO ==================================================
ECHO Running Apache Spark Word Count Example (Local Mode)
ECHO ==================================================
ECHO.

REM Check if an argument is provided for input file
IF "%~1"=="" (
    ECHO Using default sample file: data/sample.txt
    mvn exec:java
) ELSE (
    ECHO Using custom file: %~1
    mvn exec:java -Dexec.args="%~1"
)

