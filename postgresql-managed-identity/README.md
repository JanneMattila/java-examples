# PostgreSQL passwordless (Entra ID + Managed Identity) demo

Java console app that connects to **Azure Database for PostgreSQL Flexible Server** with
**Microsoft Entra ID authentication** (no passwords) and runs a query **every 10 seconds, forever**.

Based on [Quickstart: Use Java and JDBC in Azure Database for PostgreSQL Flexible Server (passwordless)](https://learn.microsoft.com/en-us/azure/postgresql/connectivity/connect-java?tabs=passwordless).

The purpose is to show that even though Entra ID access tokens expire
(~1 hour for users, ~24 hours for managed identities), the application **keeps running without
any code changes, restarts or token handling code**.

All environment-specific values (server, managed identity, ...) are kept in a git-ignored `.env`
file, see [4. Configure (.env)](#4-configure-env). Placeholders used in this document:

| Placeholder                     | Meaning                                                    | `.env` variable              |
| ------------------------------- | ---------------------------------------------------------- | ---------------------------- |
| `<server-name>`                 | Flexible Server name (`<server-name>.postgres.database.azure.com`) | `PG_HOST`            |
| `<managed-identity-name>`       | Name of the user-assigned managed identity                 | `MANAGED_IDENTITY_NAME`      |
| `<managed-identity-client-id>`  | Client ID of the managed identity                          | `MANAGED_IDENTITY_CLIENT_ID` |
| `<admin-upn>`                   | UPN of a Microsoft Entra admin of the server               | *(from `az login`)*          |
| `<rg>`, `<vm-name>`             | Resource group and VM that runs the app                    |                              |

## How it works

The JDBC URL from the portal uses a password:

```text
jdbc:postgresql://<server-name>.postgres.database.azure.com:5432/postgres?user=<admin-upn>&password={your_password}&sslmode=require
```

It is changed to a **passwordless** URL: the `password` is removed and the
[azure-identity-extensions](https://central.sonatype.com/artifact/com.azure/azure-identity-extensions)
authentication plugin is added. The user is the managed identity name and the plugin gets the
managed identity's client ID:

```text
jdbc:postgresql://<server-name>.postgres.database.azure.com:5432/postgres?sslmode=require&authenticationPluginClassName=com.azure.identity.extensions.jdbc.postgresql.AzurePostgresqlAuthenticationPlugin
  user           = <managed-identity-name>
  azure.clientId = <managed-identity-client-id>
```

(Single-string form: `...&user=<managed-identity-name>&azure.clientId=<managed-identity-client-id>`.)
The app builds this URL from the `.env` values.

Why the app survives token expiry:

1. Whenever the driver opens a **new physical connection**, the plugin asks `DefaultAzureCredential`
   for a token for `https://ossrdbms-aad.database.windows.net/.default` and sends it as the password.
2. Azure Identity caches tokens and **automatically refreshes** them before they expire, so every new
   connection gets a valid token.
3. PostgreSQL validates the token **only at login**. An established session is not terminated when
   the token later expires.
4. In the default `pool` mode HikariCP recycles connections every 30 minutes (`maxLifetime`), so new
   tokens are used continuously. In `new` mode, a brand-new connection (and token check) is made on
   every query.

The application code ([App.java](src/main/java/com/example/pgmi/App.java)) never touches a
token for the database connection. It only reads one separately for logging (identity, expiry) so you
can watch tokens being renewed. Turn this off with `SHOW_TOKEN_INFO=false`.

## 1. Install Java 21 and Maven

### Windows (what was used to build this sample)

```powershell
# Microsoft Build of OpenJDK 21 (sets JAVA_HOME and PATH)
winget install --id Microsoft.OpenJDK.21 -e

# Apache Maven (no official winget package): download, unzip and add to user PATH
$v = "3.9.16"
$dest = "$env:LOCALAPPDATA\Programs"
Invoke-WebRequest "https://dlcdn.apache.org/maven/maven-3/$v/binaries/apache-maven-$v-bin.zip" -OutFile "$env:TEMP\maven.zip"
Expand-Archive "$env:TEMP\maven.zip" $dest -Force
[Environment]::SetEnvironmentVariable("Path", [Environment]::GetEnvironmentVariable("Path","User") + ";$dest\apache-maven-$v\bin", "User")
```

Open a new terminal and check: `java -version` and `mvn -v`.

### Linux (Ubuntu)

```bash
sudo apt-get update && sudo apt-get install -y openjdk-21-jdk maven
```

## 2. Azure setup: attach the managed identity to the VM

The user-assigned managed identity must be assigned to the VM (or App Service, Container App, AKS
workload, ...) that runs the app:

```bash
az vm identity assign --resource-group <rg> --name <vm-name> \
  --identities $(az identity show --resource-group <rg> --name <managed-identity-name> --query id -o tsv)
```

If the VM has more than one identity (for example also a system-assigned one), the client ID
**must** be given (`MANAGED_IDENTITY_CLIENT_ID` in `.env`), otherwise a different identity may be used.

## 3. Admin: grant database access to the managed identity using psql

These steps are done once by a **Microsoft Entra administrator** of the server. The Entra functions
only exist in the **`postgres`** database, so connect to that database.

Install `psql` if needed (`winget install PostgreSQL.PostgreSQL.17`, `sudo apt-get install postgresql-client`)
or use [Azure Cloud Shell](https://shell.azure.com), which already has `psql` and `az`.
On Windows the installer does not add `psql` to `PATH`; add it for the current user:

```powershell
$bin = "C:\Program Files\PostgreSQL\17\bin"
[Environment]::SetEnvironmentVariable("Path", [Environment]::GetEnvironmentVariable("Path","User") + ";$bin", "User")
```

The commands below read `PG_HOST` and `MANAGED_IDENTITY_NAME` from the project's `.env` file
(copy it next to you, or set the variables manually), take the admin's user name from `az`, and pass
the identity name to psql as the variable `mi_name`. Nothing has to be edited in the SQL.

### 3.1 Sign in as the Entra admin and connect

PowerShell (run in the project folder):

```powershell
az login   # as the Entra admin of the server

# Load .env into environment variables
foreach ($line in Get-Content .env) {
    if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*?)\s*$') { Set-Item "env:$($Matches[1])" $Matches[2].Trim('"', "'") }
}

$env:PGHOST     = $env:PG_HOST
$env:PGPORT     = "5432"
$env:PGDATABASE = "postgres"
$env:PGUSER     = az account show --query user.name -o tsv
$env:PGSSLMODE  = "require"
$env:PGPASSWORD = az account get-access-token --resource-type oss-rdbms --query accessToken -o tsv
psql -v mi_name="$env:MANAGED_IDENTITY_NAME"
```

Bash (run in the project folder):

```bash
az login   # as the Entra admin of the server

set -a; source .env; set +a   # load .env into environment variables

export PGHOST="$PG_HOST"
export PGPORT=5432
export PGDATABASE=postgres
export PGUSER="$(az account show --query user.name -o tsv)"
export PGSSLMODE=require
export PGPASSWORD="$(az account get-access-token --resource-type oss-rdbms --query accessToken -o tsv)"
psql -v mi_name="$MANAGED_IDENTITY_NAME"
```

> The admin's token is valid for about one hour. Run `az account get-access-token ...` again if psql
> reports an authentication failure.

Check the variable in psql: `\echo :mi_name`. In the SQL below, `:'mi_name'` is substituted as a
string literal and `:"mi_name"` as a quoted identifier (role name).

### 3.2 Create the PostgreSQL role for the managed identity

```sql
-- Role name must equal the managed identity's name (display name in Entra ID)
SELECT * FROM pgaadauth_create_principal(:'mi_name', false, false);
```

Arguments: `role name`, `isAdmin`, `isMfa`. Use `false, false` for an application identity.

Alternative, binding explicitly to the identity's **object (principal) ID**
(useful when the role name differs from the display name, or names are not unique). Get the object ID
and start psql with it as a second variable:

```powershell
$oid = az ad sp show --id $env:MANAGED_IDENTITY_CLIENT_ID --query id -o tsv
psql -v mi_name="$env:MANAGED_IDENTITY_NAME" -v mi_oid="$oid"
```

```bash
oid="$(az ad sp show --id "$MANAGED_IDENTITY_CLIENT_ID" --query id -o tsv)"
psql -v mi_name="$MANAGED_IDENTITY_NAME" -v mi_oid="$oid"
```

```sql
SELECT * FROM pgaadauth_create_principal_with_oid(:'mi_name', :'mi_oid', 'service', false, false);
```

Verify:

```sql
SELECT * FROM pgaadauth_list_principals(false);
```

At this point the identity can log in, which is all the demo query needs
(`current_user`, `now()`, `pg_backend_pid()`).

### 3.3 (Optional) Grant data permissions

Grant only what the application needs. Run in the database the app uses
(`\c <database>`; for this demo it's `postgres`):

```sql
GRANT CONNECT ON DATABASE postgres TO :"mi_name";
GRANT USAGE ON SCHEMA public TO :"mi_name";

-- Read-only access to existing and future tables
GRANT SELECT ON ALL TABLES IN SCHEMA public TO :"mi_name";
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO :"mi_name";

-- Or read/write
-- GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO :"mi_name";
-- GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO :"mi_name";

-- Or the built-in read-only role (PostgreSQL 14+)
-- GRANT pg_read_all_data TO :"mi_name";
```

Note: `ALTER DEFAULT PRIVILEGES` applies to tables created later **by the role running the statement**.

### 3.4 Remove access

```sql
REVOKE ALL ON ALL TABLES IN SCHEMA public FROM :"mi_name";
REVOKE ALL ON SCHEMA public FROM :"mi_name";
REVOKE ALL ON DATABASE postgres FROM :"mi_name";
DROP ROLE :"mi_name";
```

## 4. Configure (.env)

All environment-specific values are read from a `.env` file in the project folder. Nothing is
hard-coded in the code, scripts or documentation. Copy the template and fill in your values:

```powershell
Copy-Item .env.example .env      # Linux: cp .env.example .env
```

```dotenv
AUTH_MODE=managed-identity
PG_HOST=<server-name>.postgres.database.azure.com
PG_DATABASE=postgres
MANAGED_IDENTITY_NAME=<managed-identity-name>
MANAGED_IDENTITY_CLIENT_ID=<managed-identity-client-id>
```

`.env` is git-ignored, so it is not committed. Real environment variables with the same name
override values in `.env`. Set `ENV_FILE` to load the file from another path.

| Variable                     | Default             | Description                                                                  |
| ---------------------------- | ------------------- | ---------------------------------------------------------------------------- |
| `AUTH_MODE`                  | `managed-identity`  | `managed-identity` or `user` (signed-in Azure CLI user)                      |
| `PG_HOST`                    | *(required)*        | Server host name                                                             |
| `PG_PORT`                    | `5432`              | Port                                                                         |
| `PG_DATABASE`                | `postgres`          | Database                                                                     |
| `MANAGED_IDENTITY_NAME`      | *(required for MI)* | PostgreSQL role of the managed identity (its name)                           |
| `MANAGED_IDENTITY_CLIENT_ID` | *(none)*            | Client ID of the user-assigned managed identity                              |
| `ENTRA_USER_NAME`            | signed-in UPN       | PostgreSQL role in `user` mode                                               |
| `AZURE_TOKEN_CREDENTIALS`    | `dev` in user mode  | Restricts `DefaultAzureCredential` to developer credentials (Azure CLI, ...) |
| `QUERY_INTERVAL_SECONDS`     | `10`                | Wait between queries                                                         |
| `CONNECTION_MODE`            | `pool`              | `pool` = HikariCP connection pool, `new` = new connection per query          |
| `POOL_MAX_LIFETIME_MINUTES`  | `30`                | How often pooled connections are replaced (each gets a new login)            |
| `SHOW_TOKEN_INFO`            | `true`              | Log identity and expiry of the current Entra ID token                        |
| `ENV_FILE`                   | `.env`              | Path of the settings file (environment variable only)                        |

## 5. Build and run

```powershell
mvn package                   # creates target/postgresql-managed-identity.jar (runnable fat jar)
```

### On the Azure VM with the managed identity (the actual demo)

```powershell
.\run-app.bat mi              # Windows
```

```bash
chmod +x run-app.sh && ./run-app.sh mi   # Linux
```

Without an argument, `AUTH_MODE` from `.env` is used. Or run the jar directly from the project
folder (it reads `.env` from the current directory):

```powershell
java -jar target\postgresql-managed-identity.jar
```

### Locally as your own Entra user (Azure CLI)

```powershell
az login
.\run-app.bat user            # or ./run-app.sh user
```

In `user` mode the PostgreSQL role is the signed-in `az` account's UPN (or `ENTRA_USER_NAME`), and
`AZURE_TOKEN_CREDENTIALS=dev` restricts `DefaultAzureCredential` to developer credentials (Azure CLI,
etc.). This is required on a VM that also has managed identities, otherwise a managed identity would
be picked first. Your user must exist as a role in the server (Entra admins already do).

Stop with `Ctrl+C`.

## 6. Demonstrating token expiry

Leave the app running longer than the token lifetime: **over 24 hours** with the managed identity,
or **over 1-1.5 hours** in `user` mode. Things to observe in the log:

- `New Entra ID token: ... expiresAt=... (valid for 23h 59m ...)` appears at start and again
  each time Azure Identity has renewed the token (shortly before expiry).
- `#N OK ...` keeps appearing every 10 seconds, with `failures=0`, also after the first token's
  `expiresAt` has passed.
- `pid` / `sessionStart` change every `POOL_MAX_LIFETIME_MINUTES` (pool mode) or on every query
  (`new` mode), proving that fresh logins with fresh tokens happen all the time.

Example output (user mode):

```text
INFO App - New Entra ID token: identity=<user-upn> oid=<user-object-id> idtyp=user expiresAt=2026-11-02T10:10:08Z (valid for 1h 07m 40s)
INFO App - #1 OK  user=<user-upn> serverTime=2026-11-02T09:02:27.305290Z pid=4616 sessionStart=2026-11-02T09:02:24.035025Z uptime=0h 00m 03s failures=0
INFO App - Current Entra ID token expires in 1h 07m 29s
INFO App - #2 OK  user=<user-upn> serverTime=2026-11-02T09:02:38.824561Z pid=4616 sessionStart=2026-11-02T09:02:24.035025Z uptime=0h 00m 15s failures=0
```

Managed identity token (`idtyp=app`, 24 hours):

```text
INFO App - New Entra ID token: identity=<managed-identity-client-id> oid=<managed-identity-object-id> idtyp=app expiresAt=2026-11-03T08:56:08Z (valid for 23h 59m 39s)
```

## Troubleshooting

| Error                                                        | Cause / fix                                                                                                          |
| ------------------------------------------------------------ | -------------------------------------------------------------------------------------------------------------------- |
| `password authentication failed for user "<managed-identity-name>"` | Role not created yet, or role name differs from the identity. Do step 3.2.                                    |
| `Microsoft Entra user token for role "X" is neither an AAD_AUTH_TOKENTYPE_APP_USER or ...` | Token belongs to a different identity (e.g. a managed identity) than the configured role. Set `MANAGED_IDENTITY_CLIENT_ID` (MI) or use `AUTH_MODE=user` (sets `AZURE_TOKEN_CREDENTIALS=dev`). |
| `ManagedIdentityCredential ... unavailable`                  | Not running on Azure, or the identity is not assigned to the VM (step 2).                                           |
| `Missing required setting ...`                               | `.env` not found or incomplete. Copy `.env.example` to `.env` and fill it in.                                       |
| Connection timeout                                           | Firewall / private networking: allow the VM's IP or VNet in the server's networking settings.                       |

## Example output

```console
$ run-app
2026-11-02 11:06:10 INFO App - Loaded settings from C:\GitHub\java-examples\postgresql-managed-identity\.env
2026-11-02 11:06:10 INFO App - Starting PostgreSQL Entra ID demo
2026-11-02 11:06:10 INFO App -   Auth mode     : MANAGED_IDENTITY
2026-11-02 11:06:10 INFO App -   Host          : pg-demo-server.postgres.database.azure.com
2026-11-02 11:06:10 INFO App -   Database      : postgres
2026-11-02 11:06:10 INFO App -   User          : id-demo-app
2026-11-02 11:06:10 INFO App -   Client ID     : 11111111-2222-3333-4444-555555555555
2026-11-02 11:06:10 INFO App -   Interval      : 10s
2026-11-02 11:06:10 INFO App -   Mode          : POOL (HikariCP, maxLifetime 30 min)
2026-11-02 11:06:10 INFO App -   JDBC URL      : jdbc:postgresql://pg-demo-server.postgres.database.azure.com:5432/postgres?sslmode=require&authenticationPluginClassName=com.azure.identity.extensions.jdbc.postgresql.AzurePostgresqlAuthenticationPlugin
2026-11-02 11:06:10 INFO HikariDataSource - pg-entra - Starting...
2026-11-02 11:06:10 INFO HikariDataSource - pg-entra - Start completed.
2026-11-02 11:06:10 INFO ChainedTokenCredential - Azure Identity => Attempted credential EnvironmentCredential is unavailable.
2026-11-02 11:06:10 INFO ChainedTokenCredential - Azure Identity => Attempted credential WorkloadIdentityCredential is unavailable.
2026-11-02 11:06:10 INFO ManagedIdentityCredential - User-assigned Managed Identity ID: 11111111-2222-3333-4444-555555555555
2026-11-02 11:06:11 INFO ChainedTokenCredential - Azure Identity => Attempted credential EnvironmentCredential is unavailable.
2026-11-02 11:06:11 INFO ChainedTokenCredential - Azure Identity => Attempted credential WorkloadIdentityCredential is unavailable.
2026-11-02 11:06:11 INFO ManagedIdentityCredential - User-assigned Managed Identity ID: 11111111-2222-3333-4444-555555555555
2026-11-02 11:06:12 INFO ManagedIdentityCredential - Azure Identity => Managed Identity environment: Managed Identity
2026-11-02 11:06:12 INFO ManagedIdentityCredential - Azure Identity => Managed Identity environment: Managed Identity
2026-11-02 11:06:12 INFO ChainedTokenCredential - Azure Identity => Attempted credential ManagedIdentityCredential returns a token
2026-11-02 11:06:12 INFO ChainedTokenCredential - Azure Identity => Attempted credential ManagedIdentityCredential returns a token
2026-11-02 11:06:12 INFO App - New Entra ID token: identity=11111111-2222-3333-4444-555555555555 oid=66666666-7777-8888-9999-000000000000 idtyp=app expiresAt=2026-11-03T10:38:44Z (valid for 23h 32m 31s)
2026-11-02 11:06:12 INFO App - #1 OK  user=id-demo-app serverTime=2026-11-02T11:06:12.501625Z pid=19621 sessionStart=2026-11-02T11:06:10.918526Z uptime=0h 00m 02s failures=0
2026-11-02 11:06:22 INFO ManagedIdentityCredential - User-assigned Managed Identity ID: 11111111-2222-3333-4444-555555555555
2026-11-02 11:06:22 INFO ManagedIdentityCredential - Azure Identity => Managed Identity environment: Managed Identity
2026-11-02 11:06:22 INFO ChainedTokenCredential - Azure Identity => Returning token from cached credential ManagedIdentityCredential
2026-11-02 11:06:22 INFO App - Current Entra ID token expires in 23h 32m 21s
2026-11-02 11:06:22 INFO App - #2 OK  user=id-demo-app serverTime=2026-11-02T11:06:22.572028Z pid=19621 sessionStart=2026-11-02T11:06:10.918526Z uptime=0h 00m 12s failures=0
2026-11-02 11:06:32 INFO ManagedIdentityCredential - User-assigned Managed Identity ID: 11111111-2222-3333-4444-555555555555
2026-11-02 11:06:32 INFO ManagedIdentityCredential - Azure Identity => Managed Identity environment: Managed Identity
2026-11-02 11:06:32 INFO ChainedTokenCredential - Azure Identity => Returning token from cached credential ManagedIdentityCredential
2026-11-02 11:06:32 INFO App - Current Entra ID token expires in 23h 32m 11s
2026-11-02 11:06:32 INFO App - #3 OK  user=id-demo-app serverTime=2026-11-02T11:06:32.596875Z pid=19621 sessionStart=2026-11-02T11:06:10.918526Z uptime=0h 00m 22s failures=0
2026-11-02 11:06:42 INFO ManagedIdentityCredential - User-assigned Managed Identity ID: 11111111-2222-3333-4444-555555555555
2026-11-02 11:06:42 INFO ManagedIdentityCredential - Azure Identity => Managed Identity environment: Managed Identity
2026-11-02 11:06:42 INFO ChainedTokenCredential - Azure Identity => Returning token from cached credential ManagedIdentityCredential
2026-11-02 11:06:42 INFO App - Current Entra ID token expires in 23h 32m 01s
2026-11-02 11:06:42 INFO App - #4 OK  user=id-demo-app serverTime=2026-11-02T11:06:42.610045Z pid=19621 sessionStart=2026-11-02T11:06:10.918526Z uptime=0h 00m 32s failures=0
2026-11-02 11:06:52 INFO ManagedIdentityCredential - User-assigned Managed Identity ID: 11111111-2222-3333-4444-555555555555
2026-11-02 11:06:52 INFO ManagedIdentityCredential - Azure Identity => Managed Identity environment: Managed Identity
2026-11-02 11:06:52 INFO ChainedTokenCredential - Azure Identity => Returning token from cached credential ManagedIdentityCredential
2026-11-02 11:06:52 INFO App - Current Entra ID token expires in 23h 31m 51s
2026-11-02 11:06:52 INFO App - #5 OK  user=id-demo-app serverTime=2026-11-02T11:06:52.628392Z pid=19621 sessionStart=2026-11-02T11:06:10.918526Z uptime=0h 00m 42s failures=0
2026-11-02 11:07:02 INFO ManagedIdentityCredential - User-assigned Managed Identity ID: 11111111-2222-3333-4444-555555555555
2026-11-02 11:07:02 INFO ManagedIdentityCredential - Azure Identity => Managed Identity environment: Managed Identity
2026-11-02 11:07:02 INFO ChainedTokenCredential - Azure Identity => Returning token from cached credential ManagedIdentityCredential
2026-11-02 11:07:02 INFO App - Current Entra ID token expires in 23h 31m 41s
2026-11-02 11:07:02 INFO App - #6 OK  user=id-demo-app serverTime=2026-11-02T11:07:02.656416Z pid=19621 sessionStart=2026-11-02T11:06:10.918526Z uptime=0h 00m 52s failures=0
2026-11-02 11:07:12 INFO ManagedIdentityCredential - User-assigned Managed Identity ID: 11111111-2222-3333-4444-555555555555
2026-11-02 11:07:12 INFO ManagedIdentityCredential - Azure Identity => Managed Identity environment: Managed Identity
2026-11-02 11:07:12 INFO ChainedTokenCredential - Azure Identity => Returning token from cached credential ManagedIdentityCredential
2026-11-02 11:07:12 INFO App - Current Entra ID token expires in 23h 31m 31s
2026-11-02 11:07:12 INFO App - #7 OK  user=id-demo-app serverTime=2026-11-02T11:07:12.679335Z pid=19621 sessionStart=2026-11-02T11:06:10.918526Z uptime=0h 01m 02s failures=0
2026-11-02 11:07:22 INFO ManagedIdentityCredential - User-assigned Managed Identity ID: 11111111-2222-3333-4444-555555555555
2026-11-02 11:07:22 INFO ManagedIdentityCredential - Azure Identity => Managed Identity environment: Managed Identity
2026-11-02 11:07:22 INFO ChainedTokenCredential - Azure Identity => Returning token from cached credential ManagedIdentityCredential
2026-11-02 11:07:22 INFO App - Current Entra ID token expires in 23h 31m 21s
2026-11-02 11:07:22 INFO App - #8 OK  user=id-demo-app serverTime=2026-11-02T11:07:22.693708Z pid=19621 sessionStart=2026-11-02T11:06:10.918526Z uptime=0h 01m 12s failures=0
2026-11-02 11:07:32 INFO ManagedIdentityCredential - User-assigned Managed Identity ID: 11111111-2222-3333-4444-555555555555
2026-11-02 11:07:32 INFO ManagedIdentityCredential - Azure Identity => Managed Identity environment: Managed Identity
2026-11-02 11:07:32 INFO ChainedTokenCredential - Azure Identity => Returning token from cached credential ManagedIdentityCredential
2026-11-02 11:07:32 INFO App - Current Entra ID token expires in 23h 31m 11s
2026-11-02 11:07:32 INFO App - #9 OK  user=id-demo-app serverTime=2026-11-02T11:07:32.917969Z pid=19621 sessionStart=2026-11-02T11:06:10.918526Z uptime=0h 01m 22s failures=0
2026-11-02 11:07:42 INFO ManagedIdentityCredential - User-assigned Managed Identity ID: 11111111-2222-3333-4444-555555555555
2026-11-02 11:07:42 INFO ManagedIdentityCredential - Azure Identity => Managed Identity environment: Managed Identity
2026-11-02 11:07:42 INFO ChainedTokenCredential - Azure Identity => Returning token from cached credential ManagedIdentityCredential
2026-11-02 11:07:42 INFO App - Current Entra ID token expires in 23h 31m 01s
2026-11-02 11:07:42 INFO App - #10 OK  user=id-demo-app serverTime=2026-11-02T11:07:42.933279Z pid=19621 sessionStart=2026-11-02T11:06:10.918526Z uptime=0h 01m 32s failures=0
2026-11-02 11:07:52 INFO ManagedIdentityCredential - User-assigned Managed Identity ID: 11111111-2222-3333-4444-555555555555
2026-11-02 11:07:52 INFO ManagedIdentityCredential - Azure Identity => Managed Identity environment: Managed Identity
2026-11-02 11:07:52 INFO ChainedTokenCredential - Azure Identity => Returning token from cached credential ManagedIdentityCredential
2026-11-02 11:07:52 INFO App - Current Entra ID token expires in 23h 30m 51s
2026-11-02 11:07:52 INFO App - #11 OK  user=id-demo-app serverTime=2026-11-02T11:07:52.954102Z pid=19621 sessionStart=2026-11-02T11:06:10.918526Z uptime=0h 01m 42s failures=0
```
