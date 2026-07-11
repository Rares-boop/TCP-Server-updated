package app.database.user;

import app.database.DatabaseConnection;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class UserLogRepository {
    private static final Logger logger = Logger.getLogger(UserLogRepository.class.getName());

    public static void insert(int userId, String actionType, long timestamp, String ipAddress) {
        String query = "INSERT INTO USER_LOGS(id_user, action_type, log_timestamp, ip_address) VALUES(?, ?, ?, ?)";

        try (var conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {

            ps.setInt(1, userId);
            ps.setString(2, actionType);
            ps.setLong(3, timestamp);
            ps.setString(4, ipAddress);
            ps.executeUpdate();

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[DATABASE] Error inserting user log", e);
        }
    }

    public static int countRecentFailedLogins(String ipAddress, int minutes) {
        String query = """
        SELECT COUNT(*) FROM USER_LOGS
        WHERE ip_address = ?
        AND action_type = 'LOGIN_FAILED'
        AND log_timestamp > ?
    """;
        try (var conn = DatabaseConnection.getConnection();
             var ps = conn.prepareStatement(query)) {

            long cutoff = System.currentTimeMillis() - (minutes * 60_000L);
            ps.setString(1, ipAddress);
            ps.setLong(2, cutoff);

            try (var rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[DATABASE] Error counting failed logins", e);
        }
        return 0;
    }

    private UserLogRepository() {
        throw new UnsupportedOperationException("Utility class");
    }
}
