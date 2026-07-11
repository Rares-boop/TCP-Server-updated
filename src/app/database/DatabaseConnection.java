package app.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.github.cdimascio.dotenv.Dotenv;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Logger;

public class DatabaseConnection {
    private static final Logger logger = Logger.getLogger(DatabaseConnection.class.getName());
    private static final HikariDataSource dataSource;

    static {
        Dotenv dotenv = Dotenv.load();

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://"
                + dotenv.get("DB_HOST") + ":"
                + dotenv.get("DB_PORT") + "/"
                + dotenv.get("DB_DATABASE"));
        config.setUsername(dotenv.get("DB_USER"));
        config.setPassword(dotenv.get("DB_PASSWORD"));

        config.setMaximumPoolSize(Integer.parseInt(dotenv.get("DB_POOL_SIZE", "10")));
        config.setMinimumIdle(2);
        config.setConnectionTimeout(3000);
        config.setIdleTimeout(30000);
        config.setMaxLifetime(600000);
        config.setPoolName("ChatAppPool");

        dataSource = new HikariDataSource(config);
        logger.info("[DATABASE] HikariCP pool initialized.");
    }

    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    private DatabaseConnection() {
        throw new UnsupportedOperationException("Utility class");
    }
}

