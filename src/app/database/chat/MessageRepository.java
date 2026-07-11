package app.database.chat;

import chat.models.Message;
import app.database.DatabaseConnection;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MessageRepository {
    private static final Logger logger = Logger.getLogger(MessageRepository.class.getName());

    public static int insertMessageReturningId(byte[] content, long timestamp, int senderId, int groupId) {
        String query = "INSERT INTO MESSAGES(content, log_timestamp, id_sender, id_group) VALUES(?,?,?,?) RETURNING id";
        try (var connection = DatabaseConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(query)) {

            ps.setBytes(1, content);
            ps.setLong(2, timestamp);
            ps.setInt(3, senderId);
            ps.setInt(4, groupId);

            try(ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[DATABASE] Error inserting message", e);
        }
        return -1;
    }

    public static List<Message> selectMessagesByGroup(int groupId) {
        List<Message> messages = new ArrayList<>();
        String query = "SELECT * FROM MESSAGES WHERE id_group=?";
        try (var connection = DatabaseConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(query)) {

            ps.setInt(1, groupId);
            try(ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    messages.add(new Message(
                            rs.getInt(1),
                            rs.getBytes(2),
                            rs.getLong(3),
                            rs.getInt(4),
                            rs.getInt(5)
                    ));
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[DATABASE] Error fetching messages for group " + groupId, e);
        }
        return messages;
    }

    public static boolean updateMessageById(int id, byte[] newContent) {
        String query = "UPDATE MESSAGES SET content=? WHERE id=?";
        try (var connection = DatabaseConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(query)) {

            ps.setBytes(1, newContent);
            ps.setInt(2, id);
            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[DATABASE] Error updating message ID: " + id, e);
            return false;
        }
    }

    public static boolean deleteMessageById(int id) {
        String query = "DELETE FROM MESSAGES WHERE id=?";
        try (var connection = DatabaseConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(query)) {

            ps.setInt(1, id);
            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[DATABASE] Error deleting message ID: " + id, e);
            return false;
        }
    }

    public static boolean isOwner(int messageId, int userId){
        String query = "SELECT 1 FROM MESSAGES WHERE id = ? AND id_sender = ?";
        try(var conn = DatabaseConnection.getConnection();
        var ps = conn.prepareStatement(query)){

            ps.setInt(1, messageId);
            ps.setInt(2, userId);

            try(ResultSet rs = ps.executeQuery()){
                return rs.next();
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[DATABASE] Error checking membership", e);
            return false;
        }
    }

    private MessageRepository() {
        throw new UnsupportedOperationException("Utility class");
    }
}
