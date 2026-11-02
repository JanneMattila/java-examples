package com.example.pgmi;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenCredential;
import com.azure.core.credential.TokenRequestContext;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Continuously queries Azure Database for PostgreSQL Flexible Server using Microsoft Entra ID
 * (passwordless) authentication. Access tokens are acquired and refreshed by the
 * azure-identity-extensions JDBC authentication plugin, so the app keeps working well past the
 * lifetime of any single token without code changes.
 */
public final class App {

    private static final Logger log = LoggerFactory.getLogger(App.class);

    private static final String AUTH_PLUGIN =
            "com.azure.identity.extensions.jdbc.postgresql.AzurePostgresqlAuthenticationPlugin";
    private static final String OSSRDBMS_SCOPE = "https://ossrdbms-aad.database.windows.net/.default";

    private static final String QUERY = """
            SELECT current_user                          AS db_user,
                   now()                                 AS server_time,
                   pg_backend_pid()                      AS backend_pid,
                   (SELECT backend_start FROM pg_stat_activity WHERE pid = pg_backend_pid()) AS session_start
            """;

    private App() {
    }

    public static void main(String[] args) throws Exception {
        Config cfg;
        try {
            cfg = Config.load();
            if (cfg.user == null) {
                cfg.user = signedInUserName();
            }
        } catch (RuntimeException e) {
            log.error("Configuration error: {}", rootMessage(e));
            System.exit(2);
            return;
        }
        log.info("Starting PostgreSQL Entra ID demo");
        log.info("  Auth mode     : {}", cfg.authMode);
        log.info("  Host          : {}", cfg.host);
        log.info("  Database      : {}", cfg.database);
        log.info("  User          : {}", cfg.user);
        log.info("  Client ID     : {}", cfg.clientId == null ? "(not set - DefaultAzureCredential chain, e.g. Azure CLI)" : cfg.clientId);
        log.info("  Interval      : {}s", cfg.intervalSeconds);
        log.info("  Mode          : {}{}", cfg.mode,
                cfg.mode == Mode.POOL ? " (HikariCP, maxLifetime " + cfg.poolMaxLifetimeMinutes + " min)" : " (new connection per query)");
        log.info("  JDBC URL      : {}", cfg.jdbcUrl());

        TokenCredential tokenInspector = cfg.showTokenInfo ? buildCredential(cfg.clientId) : null;
        HikariDataSource pool = cfg.mode == Mode.POOL ? createPool(cfg) : null;

        CountDownLatch stop = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            stop.countDown();
            if (pool != null) {
                pool.close();
            }
            log.info("Stopped.");
        }));

        OffsetDateTime startedAt = OffsetDateTime.now();
        long iteration = 0;
        long failures = 0;
        do {
            iteration++;
            if (tokenInspector != null) {
                logTokenInfo(tokenInspector);
            }
            try (Connection connection = pool != null ? pool.getConnection() : openConnection(cfg);
                 Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery(QUERY)) {
                rs.next();
                log.info("#{} OK  user={} serverTime={} pid={} sessionStart={} uptime={} failures={}",
                        iteration,
                        rs.getString("db_user"),
                        rs.getObject("server_time", OffsetDateTime.class),
                        rs.getInt("backend_pid"),
                        rs.getObject("session_start", OffsetDateTime.class),
                        formatDuration(Duration.between(startedAt, OffsetDateTime.now())),
                        failures);
            } catch (SQLException | RuntimeException e) {
                failures++;
                String message = rootMessage(e);
                log.error("#{} FAILED ({} failures so far): {}", iteration, failures, message);
                if (message.contains("password authentication failed")) {
                    log.error("Hint: the token was rejected for role \"{}\". Has an admin created the Entra principal "
                            + "(pgaadauth_create_principal) and does MANAGED_IDENTITY_NAME / ENTRA_USER_NAME match its name exactly? See README.", cfg.user);
                }
            }
        } while (!stop.await(cfg.intervalSeconds, TimeUnit.SECONDS));
    }

    private static Connection openConnection(Config cfg) throws SQLException {
        return DriverManager.getConnection(cfg.jdbcUrl(), cfg.jdbcProperties());
    }

    private static HikariDataSource createPool(Config cfg) {
        HikariConfig hc = new HikariConfig();
        hc.setPoolName("pg-entra");
        hc.setJdbcUrl(cfg.jdbcUrl());
        hc.setDataSourceProperties(cfg.jdbcProperties());
        hc.setMinimumIdle(1);
        hc.setMaximumPoolSize(2);
        // Recycle physical connections periodically; every new physical connection logs in with a fresh token.
        hc.setMaxLifetime(TimeUnit.MINUTES.toMillis(cfg.poolMaxLifetimeMinutes));
        hc.setIdleTimeout(0);
        hc.setConnectionTimeout(TimeUnit.SECONDS.toMillis(15));
        // Do not fail at startup if the database is temporarily unreachable; the loop will retry.
        hc.setInitializationFailTimeout(-1);
        return new HikariDataSource(hc);
    }

    /** User mode without ENTRA_USER_NAME: the PostgreSQL role is the UPN of the signed-in user (e.g. az login). */
    private static String signedInUserName() {
        AccessToken token = buildCredential(null).getTokenSync(new TokenRequestContext().addScopes(OSSRDBMS_SCOPE));
        String claims = decodeClaims(token.getToken());
        String upn = firstNonNull(claim(claims, "upn"), claim(claims, "unique_name"), claim(claims, "preferred_username"));
        if (upn == null) {
            throw new IllegalStateException("Cannot determine user name from token; set ENTRA_USER_NAME");
        }
        return upn;
    }

    private static TokenCredential buildCredential(String clientId) {
        DefaultAzureCredentialBuilder builder = new DefaultAzureCredentialBuilder();
        if (clientId != null) {
            builder.managedIdentityClientId(clientId);
        }
        return builder.build();
    }

    private static String lastToken;

    /**
     * Informational only: shows the identity and expiry of the Entra ID token for the database scope,
     * so you can observe tokens being renewed over time. The JDBC plugin acquires its own tokens.
     */
    private static void logTokenInfo(TokenCredential credential) {
        try {
            AccessToken token = credential.getTokenSync(new TokenRequestContext().addScopes(OSSRDBMS_SCOPE));
            String expiry = token.getExpiresAt().toString();
            Duration remaining = Duration.between(OffsetDateTime.now(), token.getExpiresAt());
            if (!token.getToken().equals(lastToken)) {
                String claims = decodeClaims(token.getToken());
                log.info("New Entra ID token: identity={} oid={} idtyp={} expiresAt={} (valid for {})",
                        firstNonNull(claim(claims, "upn"), claim(claims, "unique_name"), claim(claims, "appid")),
                        claim(claims, "oid"), claim(claims, "idtyp"), expiry, formatDuration(remaining));
                lastToken = token.getToken();
            } else {
                log.info("Current Entra ID token expires in {}", formatDuration(remaining));
            }
        } catch (RuntimeException e) {
            log.warn("Could not inspect token: {}", rootMessage(e));
        }
    }

    private static String decodeClaims(String jwt) {
        String[] parts = jwt.split("\\.");
        return parts.length < 2 ? "" : new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
    }

    private static String claim(String json, String name) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(name) + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        return m.find() ? m.group(1) : null;
    }

    private static String firstNonNull(String... values) {
        for (String v : values) {
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    private static String formatDuration(Duration d) {
        long s = Math.max(0, d.getSeconds());
        return String.format("%dh %02dm %02ds", s / 3600, (s % 3600) / 60, s % 60);
    }

    private static String rootMessage(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root == t ? String.valueOf(t.getMessage()) : t.getMessage() + " | root cause: " + root;
    }

    enum Mode { POOL, NEW }

    static final class Config {
        AuthMode authMode;
        String host;
        int port;
        String database;
        String user;
        String clientId;
        int intervalSeconds;
        Mode mode;
        int poolMaxLifetimeMinutes;
        boolean showTokenInfo;

        static Config load() {
            DotEnv env = DotEnv.load();
            Config c = new Config();
            c.authMode = AuthMode.parse(env.get("AUTH_MODE", "managed-identity"));
            c.host = env.require("PG_HOST");
            c.port = Integer.parseInt(env.get("PG_PORT", "5432"));
            c.database = env.get("PG_DATABASE", "postgres");
            if (c.authMode == AuthMode.MANAGED_IDENTITY) {
                c.user = env.require("MANAGED_IDENTITY_NAME");
                c.clientId = env.get("MANAGED_IDENTITY_CLIENT_ID", null);
            } else {
                // Restrict DefaultAzureCredential to developer credentials (Azure CLI, ...) so that a managed
                // identity on the same machine is not picked. azure-core also reads system properties.
                if (env.get("AZURE_TOKEN_CREDENTIALS", null) == null) {
                    System.setProperty("AZURE_TOKEN_CREDENTIALS", "dev");
                }
                c.user = env.get("ENTRA_USER_NAME", null);
            }
            c.intervalSeconds = Integer.parseInt(env.get("QUERY_INTERVAL_SECONDS", "10"));
            c.mode = Mode.valueOf(env.get("CONNECTION_MODE", "pool").toUpperCase());
            c.poolMaxLifetimeMinutes = Integer.parseInt(env.get("POOL_MAX_LIFETIME_MINUTES", "30"));
            c.showTokenInfo = Boolean.parseBoolean(env.get("SHOW_TOKEN_INFO", "true"));
            return c;
        }

        String jdbcUrl() {
            return "jdbc:postgresql://" + host + ":" + port + "/" + database
                    + "?sslmode=require"
                    + "&authenticationPluginClassName=" + AUTH_PLUGIN;
        }

        Properties jdbcProperties() {
            Properties p = new Properties();
            p.setProperty("user", user);
            if (clientId != null) {
                // Client ID of the user-assigned managed identity used by the authentication plugin.
                p.setProperty("azure.clientId", clientId);
            }
            p.setProperty("ApplicationName", "pg-entra-managed-identity-demo");
            return p;
        }
    }

    enum AuthMode {
        MANAGED_IDENTITY, USER;

        static AuthMode parse(String value) {
            return switch (value.toLowerCase()) {
                case "mi", "managed-identity", "managed_identity" -> MANAGED_IDENTITY;
                case "user" -> USER;
                default -> throw new IllegalArgumentException("AUTH_MODE must be 'managed-identity' or 'user', was: " + value);
            };
        }
    }

    /**
     * Minimal .env reader: KEY=VALUE lines, '#' comments, optional surrounding quotes and 'export ' prefix.
     * Real environment variables take precedence over values in the file.
     * File location: ENV_FILE environment variable, otherwise ".env" in the current directory.
     */
    static final class DotEnv {
        private final Map<String, String> values = new HashMap<>();
        private final Path file;

        private DotEnv(Path file) {
            this.file = file;
        }

        static DotEnv load() {
            String override = System.getenv("ENV_FILE");
            Path path = Path.of(override == null || override.isBlank() ? ".env" : override).toAbsolutePath();
            DotEnv env = new DotEnv(path);
            if (Files.isRegularFile(path)) {
                try {
                    for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                        String l = line.strip();
                        if (l.isEmpty() || l.startsWith("#")) {
                            continue;
                        }
                        if (l.startsWith("export ")) {
                            l = l.substring(7).strip();
                        }
                        int eq = l.indexOf('=');
                        if (eq <= 0) {
                            continue;
                        }
                        String key = l.substring(0, eq).strip();
                        String value = l.substring(eq + 1).strip();
                        if (value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\"")
                                || value.startsWith("'") && value.endsWith("'"))) {
                            value = value.substring(1, value.length() - 1);
                        }
                        env.values.put(key, value);
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException("Cannot read " + path, e);
                }
                log.info("Loaded settings from {}", path);
            } else {
                log.info("No .env file at {} - using environment variables only", path);
            }
            return env;
        }

        String get(String name, String defaultValue) {
            String v = System.getenv(name);
            if (v == null || v.isBlank()) {
                v = values.get(name);
            }
            return v == null || v.isBlank() ? defaultValue : v.strip();
        }

        String require(String name) {
            String v = get(name, null);
            if (v == null) {
                throw new IllegalStateException("Missing required setting " + name
                        + ". Set it in " + file + " (see .env.example) or as an environment variable.");
            }
            return v;
        }
    }
}
