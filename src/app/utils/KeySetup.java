package app.utils;

import crypto.api.CryptoHelper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;

public class KeySetup {
    public static void main(String[] args) throws Exception {
        String password = args.length > 0 ? args[0] : "schimba_parola_asta";

        System.out.println("[KEYGEN] Generating Dilithium key pair...");
        KeyPair kp = CryptoHelper.generateDilithiumKeys();

        SecureKeyStorage.saveEncrypted(kp.getPrivate(), Path.of("server_dilithium.enc"), password);
        Files.write(Path.of("server_dilithium.pub"), kp.getPublic().getEncoded());

        System.out.println("[KEYGEN] Private key saved (encrypted): server_dilithium.enc");
        System.out.println("[KEYGEN] Public key saved: server_dilithium.pub");
        System.out.println("[KEYGEN] Copy server_dilithium.pub to Android: app/src/main/res/raw/");
    }
}
