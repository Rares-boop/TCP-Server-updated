package app.utils;

import crypto.api.CryptoHelper;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.spec.KeySpec;
import java.util.Arrays;

public class SecureKeyStorage {

    public static void saveEncrypted(PrivateKey key, Path path, String password) throws Exception {
        byte[] salt = new byte[16];
        CryptoHelper.secureRandom.nextBytes(salt);

        SecretKey aesKey = deriveKey(password, salt);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, aesKey);
        byte[] iv = cipher.getIV();
        byte[] encrypted = cipher.doFinal(key.getEncoded());

        // Format: salt(16) + iv(12) + encrypted
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bos.write(salt);
        bos.write(iv);
        bos.write(encrypted);
        Files.write(path, bos.toByteArray());
    }

    public static byte[] loadDecrypted(Path path, String password) throws Exception {
        byte[] data = Files.readAllBytes(path);

        byte[] salt = Arrays.copyOfRange(data, 0, 16);
        byte[] iv = Arrays.copyOfRange(data, 16, 28);
        byte[] encrypted = Arrays.copyOfRange(data, 28, data.length);

        SecretKey aesKey = deriveKey(password, salt);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(128, iv));
        return cipher.doFinal(encrypted);
    }

    private static SecretKey deriveKey(String password, byte[] salt) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 600_000, 256);
        byte[] derived = factory.generateSecret(spec).getEncoded();
        return new SecretKeySpec(derived, "AES");
    }
}
