package app.database.queue;

import app.database.DatabaseConnection;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class OfflineQueueRepository {
    private static final Logger logger = Logger.getLogger(OfflineQueueRepository.class.getName());

    public static void insertPendingPacket(int targetId, String jsonPacket) {
        String query = "INSERT INTO OFFLINE_QUEUE (id_user, packet_content, created_at) VALUES (?, ?, ?)";
        try (var connection = DatabaseConnection.getConnection();
             var ps = connection.prepareStatement(query)) {

            ps.setInt(1, targetId);
            ps.setString(2, jsonPacket);
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[DATABASE] Error inserting pending packet for user " + targetId, e);
        }
    }

    public static List<String> getAndClearPendingPackets(int userId) {
        List<String> queue = new ArrayList<>();
        String selectQuery = "SELECT packet_content FROM OFFLINE_QUEUE WHERE id_user=? ORDER BY id ASC";
        String deleteQuery = "DELETE FROM OFFLINE_QUEUE WHERE id_user=?";

        try (var connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);

            try (var ps = connection.prepareStatement(selectQuery)) {
                ps.setInt(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) queue.add(rs.getString("packet_content"));
                }
            }

            if (!queue.isEmpty()) {
                try (var del = connection.prepareStatement(deleteQuery)) {
                    del.setInt(1, userId);
                    del.executeUpdate();
                }
                connection.commit();
                logger.info("[OFFLINE] Delivered " + queue.size() + " packets to User " + userId);
            } else {
                connection.rollback();
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[DATABASE] Error clearing pending packets for user " + userId, e);
        }
        return queue;
    }

    private OfflineQueueRepository() {
        throw new UnsupportedOperationException("Utility class");
    }
}
