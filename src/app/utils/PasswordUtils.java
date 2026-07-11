package server.utils;

import at.favre.lib.crypto.bcrypt.BCrypt;
import io.github.cdimascio.dotenv.Dotenv;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class PasswordUtils {
    private static final int BCRYPT_COST = 12;
    private static final Dotenv dotenv = Dotenv.load();
    private static final String PEPPER = dotenv.get("PEPPER");

    private PasswordUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static String hashPassword(String password) {
        try {
            String peppered = applyPepper(password);
            return BCrypt.withDefaults().hashToString(BCRYPT_COST, peppered.toCharArray());
        } catch (Exception e) {
            System.out.println("[BCRYPT] ERROR HASHING PASSWORD");
            e.printStackTrace();
            return null;
        }
    }

    public static boolean verifyPassword(String password, String hash) {
        try {
            String peppered = applyPepper(password);
            BCrypt.Result result = BCrypt.verifyer().verify(peppered.toCharArray(), hash);
            return result.verified;
        } catch (Exception e) {
            return false;
        }
    }

    private static String applyPepper(String password) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(PEPPER.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hmac = mac.doFinal(password.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hmac);
    }
}
