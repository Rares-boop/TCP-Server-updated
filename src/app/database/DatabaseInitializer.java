package app.database;

import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DatabaseInitializer {
    private static final Logger logger = Logger.getLogger(DatabaseInitializer.class.getName());

    public static void main(String[] args) {
        initAllTables();
        System.out.println("[DATABASE] All tables created.");
    }

    public static void initAllTables() {
        createTableUsers();
        createTableGroupChats();
        createTableGroupMembers();
        createTableUserLogs();
        createTableMessages();
        createTableOfflineQueue();
        createTableChatInvites();
        createIndexes();
    }

    private static void createTableUsers() {
        try (var conn = DatabaseConnection.getConnection();
             var stmt = conn.createStatement()) {

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS USERS(
                    id SERIAL PRIMARY KEY,
                    username VARCHAR(100) NOT NULL,
                    email VARCHAR(100) UNIQUE NOT NULL,
                    password_hash VARCHAR(255) NOT NULL,
                    created_at BIGINT,
                    confirmed BOOLEAN NOT NULL DEFAULT FALSE,
                    confirmation_token TEXT,
                    identity_key TEXT,
                    signed_pre_key TEXT,
                    signature TEXT,
                    fcm_token TEXT,
                    profile_picture TEXT
                );
            """);

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[DATABASE] Failed to create USERS table", e);
        }
    }

    private static void createTableUserLogs() {
        try (var conn = DatabaseConnection.getConnection();
             var stmt = conn.createStatement()) {

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS USER_LOGS(
                    id SERIAL PRIMARY KEY,
                    id_user INTEGER,
                    action_type VARCHAR(100),
                    log_timestamp BIGINT,
                    ip_address VARCHAR(50),
                    FOREIGN KEY(id_user) REFERENCES USERS(id)
                );
            """);

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[DATABASE] Failed to create USER_LOGS table", e);
        }
    }

    private static void createTableGroupChats() {
        try (var conn = DatabaseConnection.getConnection();
             var stmt = conn.createStatement()) {

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS GROUP_CHATS(
                    id SERIAL PRIMARY KEY,
                    name VARCHAR(200) NOT NULL
                );
            """);

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[DATABASE] Failed to create GROUP_CHATS table", e);
        }
    }

    private static void createTableGroupMembers() {
        try (var conn = DatabaseConnection.getConnection();
             var stmt = conn.createStatement()) {

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS GROUP_MEMBERS(
                    id_group INTEGER,
                    id_user INTEGER,
                    PRIMARY KEY(id_group, id_user),
                    FOREIGN KEY(id_group) REFERENCES GROUP_CHATS(id) ON DELETE CASCADE,
                    FOREIGN KEY(id_user) REFERENCES USERS(id) ON DELETE CASCADE
                );
            """);

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[DATABASE] Failed to create GROUP_MEMBERS table", e);
        }
    }

    private static void createTableMessages() {
        try (var conn = DatabaseConnection.getConnection();
             var stmt = conn.createStatement()) {

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS MESSAGES(
                    id SERIAL PRIMARY KEY,
                    content BYTEA NOT NULL,
                    log_timestamp BIGINT NOT NULL,
                    id_sender INTEGER NOT NULL,
                    id_group INTEGER NOT NULL,
                    FOREIGN KEY(id_sender) REFERENCES USERS(id) ON DELETE CASCADE,
                    FOREIGN KEY(id_group) REFERENCES GROUP_CHATS(id) ON DELETE CASCADE
                );
            """);

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[DATABASE] Failed to create MESSAGES table", e);
        }
    }

    private static void createTableOfflineQueue() {
        try (var conn = DatabaseConnection.getConnection();
             var stmt = conn.createStatement()) {

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS OFFLINE_QUEUE(
                    id SERIAL PRIMARY KEY,
                    id_user INTEGER NOT NULL,
                    packet_content TEXT NOT NULL,
                    created_at BIGINT,
                    FOREIGN KEY(id_user) REFERENCES USERS(id) ON DELETE CASCADE
                );
            """);

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[DATABASE] Failed to create OFFLINE_QUEUE table", e);
        }
    }

    private static void createTableChatInvites(){
        try(var connection = DatabaseConnection.getConnection();
        var stmt = connection.createStatement()){

            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS CHAT_INVITES (
                                id SERIAL PRIMARY KEY,
                                id_sender INTEGER NOT NULL,
                                id_receiver INTEGER NOT NULL,
                                status VARCHAR(20) DEFAULT 'PENDING',
                                created_at BIGINT,
                                UNIQUE(id_sender, id_receiver),
                                FOREIGN KEY(id_sender) REFERENCES USERS(id) ON DELETE CASCADE,
                                FOREIGN KEY(id_receiver) REFERENCES USERS(id) ON DELETE CASCADE
                            );
            """);

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[DATABASE] Failed to create CHAT_INVITES table", e);
        }
    }

    private static void createIndexes() {
        String[] indexes = {
                "CREATE INDEX IF NOT EXISTS idx_users_email ON USERS(email)",
                "CREATE INDEX IF NOT EXISTS idx_messages_group ON MESSAGES(id_group)",
                "CREATE INDEX IF NOT EXISTS idx_messages_sender ON MESSAGES(id_sender)",
                "CREATE INDEX IF NOT EXISTS idx_group_members_user ON GROUP_MEMBERS(id_user)",
                "CREATE INDEX IF NOT EXISTS idx_group_members_group ON GROUP_MEMBERS(id_group)",
                "CREATE INDEX IF NOT EXISTS idx_offline_queue_user ON OFFLINE_QUEUE(id_user)",
                "CREATE INDEX IF NOT EXISTS idx_user_logs_ip ON USER_LOGS(ip_address, action_type)",
                "CREATE INDEX IF NOT EXISTS idx_invites_receiver ON CHAT_INVITES(receiver_id, status)",
        };

        try (var conn = DatabaseConnection.getConnection();
             var stmt = conn.createStatement()) {
            for (String idx : indexes) {
                stmt.executeUpdate(idx);
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[DATABASE] Error creating indexes", e);
        }
    }

    private DatabaseInitializer() {
        throw new UnsupportedOperationException("Utility class");
    }
}
