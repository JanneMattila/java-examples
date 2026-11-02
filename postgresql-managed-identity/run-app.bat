@ECHO OFF
SETLOCAL
REM Usage: run-app.bat [mi|user]
REM   mi   - authenticate with the user-assigned managed identity (run on the Azure VM)
REM   user - authenticate as the signed-in Azure CLI user (az login)
REM Without an argument AUTH_MODE from .env is used (default: managed identity).
REM All settings (server, database, identity) are read by the app from .env - see .env.example.

CD /D "%~dp0"

IF NOT EXIST .env (
    ECHO .env not found. Copy .env.example to .env and fill in your values.
    EXIT /B 1
)

IF /I "%~1"=="mi" SET AUTH_MODE=managed-identity
IF /I "%~1"=="user" SET AUTH_MODE=user

IF NOT EXIST target\postgresql-managed-identity.jar (
    CALL mvn -q -B package || EXIT /B 1
)

java -jar target\postgresql-managed-identity.jar
