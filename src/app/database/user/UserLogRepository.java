package app.database.user;

import app.database.DatabaseConnection;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class UserLogRepository {
    private static final Logger logger = Logger.getLogger(UserLogRepository.class.getName());

    public static void insert(Integer userId, String actionType, long timestamp, String ipAddress) {
        String query = "INSERT INTO USER_LOGS(id_user, action_type, log_timestamp, ip_address) VALUES(?, ?, ?, ?)";

        try (var conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {

            if (userId != null) {
                ps.setInt(1, userId);
            } else {
                ps.setNull(1, java.sql.Types.INTEGER);
            }
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

    public static void deleteOlderThan(int days) {
        String query = "DELETE FROM USER_LOGS WHERE log_timestamp < ?";
        try (var conn = DatabaseConnection.getConnection();
             var ps = conn.prepareStatement(query)) {

            ps.setLong(1, System.currentTimeMillis() - (days * 86_400_000L));
            int deleted = ps.executeUpdate();
            if (deleted > 0) logger.info("[DATABASE] Cleaned " + deleted + " old logs");

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[DATABASE] Error cleaning logs", e);
        }
    }

    private UserLogRepository() {
        throw new UnsupportedOperationException("Utility class");
    }
}
