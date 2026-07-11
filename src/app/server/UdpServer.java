package app.server;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class UdpServer {
    private static final Logger logger = Logger.getLogger(UdpServer.class.getName());
    public static volatile boolean isUdpServerRunning = true;

    public static final Map<Integer, InetSocketAddress> activeCallers = new ConcurrentHashMap<>();
    public static final Map<Integer, InetSocketAddress> activeVideo = new ConcurrentHashMap<>();

    public static void start(int udpPort, Map<Integer, InetSocketAddress> activeQueue) {
        ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
        BlockingQueue<DatagramPacket> sendQueue = new LinkedBlockingQueue<>();

        try (DatagramSocket udpSocket = new DatagramSocket(udpPort)) {
            udpSocket.setReceiveBufferSize(4 * 1024 * 1024);

            System.out.println("[SERVER] UDP Server active on port " + udpPort);
            byte[] packetData = new byte[65000];

            Thread.startVirtualThread(() -> {
                while (isUdpServerRunning) {
                    try {
                        DatagramPacket out = sendQueue.take();
                        udpSocket.send(out);
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "UDP send error", e);
                    }
                }
            });

            while (isUdpServerRunning) {
                DatagramPacket packet = new DatagramPacket(packetData, packetData.length);
                udpSocket.receive(packet);

                byte[] dataCopy = new byte[packet.getLength()];
                System.arraycopy(packet.getData(), 0, dataCopy, 0, packet.getLength());
                InetAddress senderAddress = packet.getAddress();
                int senderPort = packet.getPort();

                pool.submit(() -> {
                    if (dataCopy.length < 8) return;

                    try {
                        ByteBuffer buf = ByteBuffer.wrap(dataCopy);

                        int senderId = buf.getInt();
                        int targetId = buf.getInt();

                        InetSocketAddress senderAddr = new InetSocketAddress(
                                senderAddress, senderPort
                        );
                        activeQueue.put(senderId, senderAddr);

                        InetSocketAddress targetAddr = activeQueue.get(targetId);
                        if (targetAddr != null) {
                            sendQueue.put(new DatagramPacket(dataCopy, dataCopy.length, targetAddr));
                        }
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "UDP routing error", e);
                    }
                });
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Critical error in UDP server", e);
        }
    }
}
