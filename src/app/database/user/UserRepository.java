package app.database.user;

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

    public static List<String> selectUsersAddConversation() {
        List<String> users = new ArrayList<>();
        try (var connection = DatabaseConnection.getConnection();
             var stmt = connection.createStatement()) {

            try(ResultSet rs = stmt.executeQuery("SELECT id, username FROM USERS")) {
                while (rs.next()) {
                    users.add(rs.getInt("id") + "," + rs.getString("username"));
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[DATABASE] Error fetching users for conversation", e);
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
