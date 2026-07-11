package app.database.chat;

import chat.models.GroupChat;
import chat.models.GroupMember;
import app.database.DatabaseConnection;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GroupChatRepository {
    private static final Logger logger = Logger.getLogger(GroupChatRepository.class.getName());

    public static GroupChat insertGroupChatReturningId(String name) {
        String query = "INSERT INTO GROUP_CHATS(name) VALUES(?) RETURNING id";
        try (var connection = DatabaseConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(query)) {

            ps.setString(1, name);
            try(ResultSet rs = ps.executeQuery()){
                if(rs.next()){
                    return new GroupChat(rs.getInt(1), name);
                }
            }

            logger.info("[DATABASE] Group chat inserted: " + name);

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[DATABASE] Error inserting group chat", e);
        }
        return null;
    }

    public static GroupChat selectChatBetweenUsers(int userId1, int userId2) {
        String query = """
        SELECT gc.id, gc.name
        FROM GROUP_CHATS gc
        JOIN GROUP_MEMBERS gm1 ON gc.id = gm1.id_group AND gm1.id_user = ?
        JOIN GROUP_MEMBERS gm2 ON gc.id = gm2.id_group AND gm2.id_user = ?
        WHERE (SELECT COUNT(*) FROM GROUP_MEMBERS WHERE id_group = gc.id) = 2
        LIMIT 1
    """;
        try (var connection = DatabaseConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(query)) {
            ps.setInt(1, userId1);
            ps.setInt(2, userId2);
            try(ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new GroupChat(rs.getInt("id"), rs.getString("name"));
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[DATABASE] Error checking existing chat", e);
        }
        return null;
    }

    public static List<GroupChat> selectGroupChatsByUserId(int userId) {
        List<GroupChat> chats = new ArrayList<>();
        String query = """
            SELECT GROUP_CHATS.id, GROUP_CHATS.name
            FROM GROUP_CHATS
            JOIN GROUP_MEMBERS ON GROUP_CHATS.id = GROUP_MEMBERS.id_group
            WHERE id_user=?
        """;
        try (var connection = DatabaseConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(query)) {

            ps.setInt(1, userId);
            try(ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    chats.add(new GroupChat(rs.getInt(1), rs.getString(2)));
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[DATABASE] Error fetching group chats for user " + userId, e);
        }
        return chats;
    }

    public static boolean updateGroupChatName(int chatId, String newName) {
        String query = "UPDATE GROUP_CHATS SET name=? WHERE id=?";
        try (var connection = DatabaseConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(query)) {

            ps.setString(1, newName);
            ps.setInt(2, chatId);
            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[DATABASE] Error updating group chat name", e);
            return false;
        }
    }

    public static boolean deleteGroupChatTransactional(int chatId) {
        try (var connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try (
                    var psMsg = connection.prepareStatement("DELETE FROM MESSAGES WHERE id_group=?");
                    var psMem = connection.prepareStatement("DELETE FROM GROUP_MEMBERS WHERE id_group=?");
                    var psChat = connection.prepareStatement("DELETE FROM GROUP_CHATS WHERE id=?")
            ) {
                psMsg.setInt(1, chatId); psMsg.executeUpdate();
                psMem.setInt(1, chatId); psMem.executeUpdate();
                psChat.setInt(1, chatId);
                int rows = psChat.executeUpdate();

                connection.commit();
                return rows > 0;

            } catch (SQLException e) {
                connection.rollback();
                logger.log(Level.SEVERE, "[DATABASE] Transaction failed. Rolled back chat deletion: " + chatId, e);
                return false;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[DATABASE] Error during transactional group delete", e);
            return false;
        }
    }

    public static void insertGroupMember(int groupId, int userId) {
        String query = "INSERT INTO GROUP_MEMBERS(id_group, id_user) VALUES(?, ?)";
        try (var connection = DatabaseConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(query)) {

            ps.setInt(1, groupId);
            ps.setInt(2, userId);
            ps.executeUpdate();
            logger.info("[DATABASE] User " + userId + " added to Group " + groupId);

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[DATABASE] Error inserting group member", e);
        }
    }

    public static List<GroupMember> selectGroupMembersByChatId(int groupId) {
        List<GroupMember> members = new ArrayList<>();
        String query = "SELECT id_group, id_user FROM GROUP_MEMBERS WHERE id_group=?";
        try (var connection = DatabaseConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(query)) {

            ps.setInt(1, groupId);
            try(ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    members.add(new GroupMember(rs.getInt(1), rs.getInt(2)));
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[DATABASE] Error fetching group members for group " + groupId, e);
        }
        return members;
    }

    public static boolean isMember(int chatId, int userId) {
        String query = "SELECT 1 FROM GROUP_MEMBERS WHERE id_group = ? AND id_user = ?";
        try (var conn = DatabaseConnection.getConnection();
             var ps = conn.prepareStatement(query)) {

            ps.setInt(1, chatId);
            ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[DATABASE] Error checking membership", e);
            return false;
        }
    }

    private GroupChatRepository() {
        throw new UnsupportedOperationException("Utility class");
    }
}
