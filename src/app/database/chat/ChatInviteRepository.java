package app.database.chat;

import app.database.DatabaseConnection;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ChatInviteRepository {
    private static final Logger logger = Logger.getLogger(ChatInviteRepository.class.getName());

    public static int insertInvite(int senderId, int receiverId){
        String query = "INSERT INTO CHAT_INVITES(id_sender, id_receiver, status, created_at) VALUES (?, ?, 'PENDING', ?) RETURNING id";

        try(var connection = DatabaseConnection.getConnection();
        var ps = connection.prepareStatement(query)){

            ps.setInt(1, senderId);
            ps.setInt(2, receiverId);
            ps.setLong(3, System.currentTimeMillis());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }

        } catch (SQLException e) {
            if (e.getSQLState() != null && e.getSQLState().startsWith("23")) {
                logger.info("[INVITE] Invite already exists: " + senderId + " -> " + receiverId);
                return -2;
            }
            logger.log(Level.SEVERE, "[INVITE] Error inserting invite", e);
        }

        return -1;
    }

    public static List<Map<String, Object>> getPendingInvitesForUser(int userId) {
        List<Map<String, Object>> invites = new ArrayList<>();
        String query = """
            SELECT ci.id, ci.id_sender, u.username AS sender_name, ci.created_at
            FROM CHAT_INVITES ci
            JOIN USERS u ON u.id = ci.id_sender
            WHERE ci.id_receiver = ? AND ci.status = 'PENDING'
            ORDER BY ci.created_at DESC
        """;

        try (var conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> invite = new HashMap<>();
                    invite.put("inviteId", rs.getInt("id"));
                    invite.put("senderId", rs.getInt("id_sender"));
                    invite.put("senderName", rs.getString("sender_name"));
                    invite.put("createdAt", rs.getLong("created_at"));
                    invites.add(invite);
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[INVITE] Error fetching pending invites for user " + userId, e);
        }
        return invites;
    }

    public static int getInviteSenderId(int inviteId, int receiverId){
        String query = "SELECT id_sender FROM CHAT_INVITES WHERE id = ? AND id_receiver = ? AND status = 'PENDING'";

        try(var connection = DatabaseConnection.getConnection();
        var ps = connection.prepareStatement(query)){

            ps.setInt(1, inviteId);
            ps.setInt(2, receiverId);

            try(ResultSet rs = ps.executeQuery()){
                if(rs.next()){
                    return rs.getInt("id_sender");
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[INVITE] Error fetching invite sender", e);
        }

        return -1;
    }

    public static boolean acceptInvite(int inviteId, int receiverId){
        String query = "UPDATE CHAT_INVITES SET status = 'ACCEPTED' WHERE id = ? AND id_receiver = ? AND status = 'PENDING'";

        try(var connection = DatabaseConnection.getConnection();
        var ps = connection.prepareStatement(query)){

            ps.setInt(1, inviteId);
            ps.setInt(2, receiverId);

            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[INVITE] Error accepting invite " + inviteId, e);
            return false;
        }
    }

    public static boolean denyInvite(int inviteId, int receiverId){
        String query = "DELETE FROM CHAT_INVITES WHERE id = ? AND id_receiver = ? AND status = 'PENDING'";

        try(var connection = DatabaseConnection.getConnection();
        var ps = connection.prepareStatement(query)){

            ps.setInt(1, inviteId);
            ps.setInt(2, receiverId);

            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[INVITE] Error denying invite " + inviteId, e);
            return false;
        }

    }

    public static boolean hasExistingInviteOrChat(int userId1, int userId2) {
        String query = """
            SELECT 1 FROM CHAT_INVITES
            WHERE ((id_sender = ? AND id_receiver = ?) OR (id_sender = ? AND id_receiver = ?))
            AND status = 'PENDING'
        """;

        try (var conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setInt(1, userId1);
            ps.setInt(2, userId2);
            ps.setInt(3, userId2);
            ps.setInt(4, userId1);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[INVITE] Error checking existing invite", e);
            return false;
        }
    }

    public static boolean deleteAllForUser(int userId){
        String query = "DELETE FROM CHAT_INVITES WHERE id_sender = ? OR id_receiver = ?";

        try(var connection = DatabaseConnection.getConnection();
        var ps = connection.prepareStatement(query)){

            ps.setInt(1, userId);
            ps.setInt(2, userId);
            ps.executeUpdate();

            return true;

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[INVITE] Error deleting invites for user " + userId, e);
            return false;
        }

    }

    public static void deleteExpiredPending(int days) {
        String query = "DELETE FROM CHAT_INVITES WHERE status = 'PENDING' AND created_at < ?";
        try (var conn = DatabaseConnection.getConnection();
             var ps = conn.prepareStatement(query)) {
            ps.setLong(1, System.currentTimeMillis() - (days * 86_400_000L));
            int deleted = ps.executeUpdate();
            if (deleted > 0) logger.info("[INVITE] Cleaned " + deleted + " expired pending invites");
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[INVITE] Error cleaning expired invites", e);
        }
    }

    public static void deleteInviteBetween(int userId1, int userId2) {
        String query = "DELETE FROM CHAT_INVITES WHERE " +
                "(id_sender = ? AND id_receiver = ?) OR (id_sender = ? AND id_receiver = ?)";
        try (var conn = DatabaseConnection.getConnection();
             var ps = conn.prepareStatement(query)) {
            ps.setInt(1, userId1);
            ps.setInt(2, userId2);
            ps.setInt(3, userId2);
            ps.setInt(4, userId1);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[INVITE] Error deleting invite between users", e);
        }
    }

    private ChatInviteRepository() {
        throw new UnsupportedOperationException("Utility class");
    }
}
