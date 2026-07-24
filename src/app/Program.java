package app;

import app.database.user.UserLogRepository;
import app.server.TcpServer;
import app.server.UdpServer;
import app.utils.FcmService;
import app.utils.SecureKeyStorage;
import crypto.api.CryptoHelper;
import io.github.cdimascio.dotenv.Dotenv;

import java.nio.file.Path;
import java.security.PrivateKey;
import java.util.Base64;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Program {
    private static final Logger logger = Logger.getLogger(Program.class.getName());
    private static final Dotenv dotenv = Dotenv.load();

    private static final int UDP_AUDIO_PORT = Integer.parseInt(dotenv.get("UDP_AUDIO_PORT", "25556"));
    private static final int UDP_VIDEO_PORT = Integer.parseInt(dotenv.get("UDP_VIDEO_PORT", "25557"));

    public static void main(String[] args) {
        try {
            Path keyPath = Path.of(dotenv.get("DILITHIUM_KEY_PATH", "server_dilithium.enc"));
            String keyPass = dotenv.get("DILITHIUM_KEY_PASSWORD");
            byte[] keyBytes = SecureKeyStorage.loadDecrypted(keyPath, keyPass);
            String keyBase64 = Base64.getEncoder().encodeToString(keyBytes);
            PrivateKey serverKey = CryptoHelper.stringToDilithiumPrivate(keyBase64);
            
            FcmService.init();

            System.out.println("[SERVER] Server starting...");
            Thread.startVirtualThread(()->TcpServer.start(serverKey));

            Thread.startVirtualThread(() -> UdpServer.start(UDP_AUDIO_PORT, UdpServer.activeCallers));
            Thread.startVirtualThread(() -> UdpServer.start(UDP_VIDEO_PORT, UdpServer.activeVideo));

            Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(
                    () -> UserLogRepository.deleteOlderThan(30),
                    1, 24, TimeUnit.HOURS
            );

            Thread.currentThread().join();

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Something went wrong", e);
        }
    }
}
