package app.database.user;

import app.database.chat.ChatInviteRepository;
import chat.models.User;
import chat.network.ChatDtos;
import app.database.DatabaseConnection;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class UserRepository {
    private static final Logger logger = Logger.getLogger(UserRepository.class.getName());

    public static String insertUser(String username, String email, String passwordHash, long createdAt) {
        String token = String.format("%06d", new java.security.SecureRandom().nextInt(1_000_000));
        String query = "INSERT INTO users(username, email, password_hash, created_at, confirmation_token) VALUES (?, ?, ?, ?, ?)";
        try (var connection = DatabaseConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(query)) {

            ps.setString(1, username);
            ps.setString(2, email);
            ps.setString(3, passwordHash);
            ps.setLong(4, createdAt);
            ps.setString(5, token);
            ps.executeUpdate();

            logger.info("[DATABASE] User inserted: " + username);

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[DATABASE] Error inserting user: " + username, e);
            return null;
        }

        return token;
    }

    public static User selectUserByEmail(String email) {
        String query = """
            SELECT id, username, email, password_hash, created_at, confirmed,
                           identity_key, signed_pre_key, signature
                    FROM USERS WHERE email = ?
        """;
        try (var connection = DatabaseConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(query)) {
            ps.setString(1, email);

            try(ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapUser(rs);
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[DATABASE] Error selecting user: " + email, e);
        }
        return null;
    }

    public static List<String> selectUsersAddConversation(String search, int limit) {
        List<String> users = new ArrayList<>();
        String query;

        if (search != null && !search.isEmpty()) {
            query = "SELECT id, username FROM USERS WHERE confirmed = TRUE AND username ILIKE ? LIMIT ?";
        } else {
            query = "SELECT id, username FROM USERS WHERE confirmed = TRUE LIMIT ?";
        }

        try (var conn = DatabaseConnection.getConnection();
             var ps = conn.prepareStatement(query)) {

            if (search != null && !search.isEmpty()) {
                ps.setString(1, "%" + search + "%");
                ps.setInt(2, limit);
            } else {
                ps.setInt(1, limit);
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    users.add(rs.getInt("id") + "," + rs.getString("username"));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[DATABASE] Error fetching users", e);
        }
        return users;
    }

    public static boolean updateUserKeys(int userId, String ik, String spk, String sig) {
        String query = "UPDATE users SET identity_key=?, signed_pre_key=?, signature=? WHERE id=?";
        try (var connection = DatabaseConnection.getConnection();
             var ps = connection.prepareStatement(query)) {

            ps.setString(1, ik);
            ps.setString(2, spk);
            ps.setString(3, sig);
            ps.setInt(4, userId);

            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[DATABASE] Error updating keys for user " + userId, e);
            return false;
        }
    }

    public static ChatDtos.GetBundleResponseDto selectUserKeys(int targetUserId) {
        String query = "SELECT identity_key, signed_pre_key, signature FROM users WHERE id=?";
        try (var connection = DatabaseConnection.getConnection();
             var ps = connection.prepareStatement(query)) {

            ps.setInt(1, targetUserId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                String ik = rs.getString("identity_key");
                String spk = rs.getString("signed_pre_key");
                String sig = rs.getString("signature");
                if (ik == null || spk == null) return null;
                return new ChatDtos.GetBundleResponseDto(targetUserId, ik, spk, sig);
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[DATABASE] Error fetching keys for user " + targetUserId, e);
        }
        return null;
    }

    public static boolean confirmUser(String token){
        String query = "UPDATE USERS SET confirmed = TRUE, confirmation_token = NULL WHERE confirmation_token = ? AND confirmed = FALSE";
        try(var connection = DatabaseConnection.getConnection();
        var ps = connection.prepareStatement(query)){

            ps.setString(1, token);

            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[DATABASE] Error updating token  " + token, e);
            return false;
        }
    }

    public static boolean updateFcmToken(int userId, String fcmToken){
        String query = "UPDATE USERS SET fcm_token = ? WHERE id = ?";
        try(var connection = DatabaseConnection.getConnection();
        PreparedStatement ps = connection.prepareStatement(query)){

            ps.setString(1, fcmToken);
            ps.setInt(2, userId);

            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[DATABASE] Error updating FCM token for user " + userId, e);
            return false;
        }
    }

    public static String getFcmToken(int userId){
        String query = "SELECT fcm_token FROM USERS WHERE id = ?";
        try(var connection = DatabaseConnection.getConnection();
        PreparedStatement ps = connection.prepareStatement(query)){

            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("fcm_token");
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[DATABASE] Error fetching FCM token for user " + userId, e);
        }

        return null;
    }

    public static boolean clearFcmToken(int userId){
        String query = "UPDATE USERS SET fcm_token = NULL WHERE id = ?";
        try(var connection = DatabaseConnection.getConnection();
        PreparedStatement ps = connection.prepareStatement(query)){

            ps.setInt(1, userId);
            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[DATABASE] Error clearing FCM token for user " + userId, e);
            return false;
        }
    }

    public static boolean updateProfilePicture(int userId, String base64Image){
        String query = "UPDATE USERS SET profile_picture=? WHERE id=?";
        try(var connection = DatabaseConnection.getConnection();
        PreparedStatement ps = connection.prepareStatement(query)){

            ps.setString(1, base64Image);
            ps.setInt(2, userId);

            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[DATABASE] Error updating profile picture for user " + userId, e);
            return false;
        }

    }

    public static String getProfilePicture(int userId){
        String query = "SELECT profile_picture FROM USERS WHERE id=?";
        try(var connection = DatabaseConnection.getConnection();
        PreparedStatement ps = connection.prepareStatement(query)){

            ps.setInt(1, userId);
            try(ResultSet rs = ps.executeQuery()){
                if(rs.next()){
                    return rs.getString("profile_picture");
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[DATABASE] Error fetching profile picture for user " + userId, e);
        }

        return null;
    }

    public static boolean deleteUserAccount(int userId){
        try(var connection = DatabaseConnection.getConnection()){
            connection.setAutoCommit(false);

            try{

                String offlineQueueQuery = "DELETE FROM OFFLINE_QUEUE WHERE id_user=?";
                try(var ps = connection.prepareStatement(offlineQueueQuery)){
                    ps.setInt(1, userId);
                    ps.executeUpdate();
                }

                String userLogsQuery = "DELETE FROM USER_LOGS WHERE id_user=?";
                try(var ps = connection.prepareStatement(userLogsQuery)){
                    ps.setInt(1, userId);
                    ps.executeUpdate();
                }

                ChatInviteRepository.deleteAllForUser(userId);

                List<Integer> userChatIds = new ArrayList<>();
                try (var ps = connection.prepareStatement("SELECT id_group FROM GROUP_MEMBERS WHERE id_user = ?")) {
                    ps.setInt(1, userId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            userChatIds.add(rs.getInt("id_group"));
                        }
                    }
                }

                for (int chatId : userChatIds) {
                    try (var ps = connection.prepareStatement("DELETE FROM MESSAGES WHERE id_group = ?")) {
                        ps.setInt(1, chatId);
                        ps.executeUpdate();
                    }
                    try (var ps = connection.prepareStatement("DELETE FROM GROUP_MEMBERS WHERE id_group = ?")) {
                        ps.setInt(1, chatId);
                        ps.executeUpdate();
                    }
                    try (var ps = connection.prepareStatement("DELETE FROM GROUP_CHATS WHERE id = ?")) {
                        ps.setInt(1, chatId);
                        ps.executeUpdate();
                    }
                }

                String usersQuery = "DELETE FROM USERS WHERE id=?";
                try(var ps = connection.prepareStatement(usersQuery)){
                    ps.setInt(1, userId);
                    ps.executeUpdate();
                }

                connection.commit();
                logger.info("[DATABASE] User " + userId + " account deleted completely.");
                return true;

            } catch (SQLException e) {
                connection.rollback();
                logger.log(Level.SEVERE, "[DATABASE] Error deleting user " + userId + ". Rolled back.", e);
                return false;
            }finally {
                connection.setAutoCommit(true);
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[DATABASE] Connection error during delete for user " + userId, e);
            return false;
        }
    }

    public static void deleteUnconfirmedOlderThan(int days) {
        String query = "DELETE FROM USERS WHERE confirmed = FALSE AND created_at < ?";
        try (var conn = DatabaseConnection.getConnection();
             var ps = conn.prepareStatement(query)) {
            ps.setLong(1, System.currentTimeMillis() - (days * 86_400_000L));
            int deleted = ps.executeUpdate();
            if (deleted > 0) logger.info("[DATABASE] Cleaned " + deleted + " unconfirmed accounts");
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[DATABASE] Error cleaning unconfirmed accounts", e);
        }
    }

    public static User selectUserByUsername(String username) {
        String query = "SELECT id, username, email, password_hash, created_at, confirmed, identity_key, signed_pre_key, signature FROM USERS WHERE username = ?";
        try (var conn = DatabaseConnection.getConnection();
             var ps = conn.prepareStatement(query)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapUser(rs);
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[DATABASE] Error selecting user by username: " + username, e);
        }
        return null;
    }

    public static boolean saveResetToken(String email, String token) {
        String query = "UPDATE USERS SET confirmation_token = ? WHERE email = ? AND confirmed = TRUE";
        try (var conn = DatabaseConnection.getConnection();
             var ps = conn.prepareStatement(query)) {
            ps.setString(1, token);
            ps.setString(2, email);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[DATABASE] Error saving reset token", e);
            return false;
        }
    }

    public static boolean resetPassword(String token, String newPasswordHash) {
        String query = "UPDATE USERS SET password_hash = ?, confirmation_token = NULL WHERE confirmation_token = ? AND confirmed = TRUE";
        try (var conn = DatabaseConnection.getConnection();
             var ps = conn.prepareStatement(query)) {
            ps.setString(1, newPasswordHash);
            ps.setString(2, token);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[DATABASE] Error resetting password", e);
            return false;
        }
    }

    private static User mapUser(ResultSet rs) throws SQLException {
        return new User(
                rs.getInt("id"),
                rs.getString("username"),
                rs.getString("email"),
                rs.getString("password_hash"),
                rs.getLong("created_at"),
                rs.getBoolean("confirmed"),
                rs.getString("identity_key"),
                rs.getString("signed_pre_key"),
                rs.getString("signature")
        );
    }

    private UserRepository() {
        throw new UnsupportedOperationException("Utility class");
    }
}
