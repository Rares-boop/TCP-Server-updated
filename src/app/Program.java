package app;

import app.server.TcpServer;
import app.server.UdpServer;
import io.github.cdimascio.dotenv.Dotenv;

import java.util.logging.Level;
import java.util.logging.Logger;

public class Program {
    private static final Logger logger = Logger.getLogger(Program.class.getName());
    private static final Dotenv dotenv = Dotenv.load();

    private static final int UDP_AUDIO_PORT = Integer.parseInt(dotenv.get("UDP_AUDIO_PORT", "25556"));
    private static final int UDP_VIDEO_PORT = Integer.parseInt(dotenv.get("UDP_VIDEO_PORT", "25557"));

    public static void main(String[] args) {
        try {
            System.out.println("[SERVER] Server starting...");
            Thread.startVirtualThread(TcpServer::start);

            Thread.startVirtualThread(() -> UdpServer.start(UDP_AUDIO_PORT, UdpServer.activeCallers));
            Thread.startVirtualThread(() -> UdpServer.start(UDP_VIDEO_PORT, UdpServer.activeVideo));

            Thread.currentThread().join();

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Something went wrong", e);
        }
    }
}
