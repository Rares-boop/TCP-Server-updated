package app.server;

import chat.models.GroupChat;
import chat.models.GroupMember;
import chat.models.Message;
import chat.models.User;
import chat.network.ChatDtos;
import chat.network.NetworkPacket;
import chat.network.PacketType;
import chat.security.CryptoHelper;
import com.google.gson.Gson;
import io.github.cdimascio.dotenv.Dotenv;
import app.database.chat.GroupChatRepository;
import app.database.chat.MessageRepository;
import app.database.queue.OfflineQueueRepository;
import app.database.user.UserLogRepository;
import app.database.user.UserRepository;
import app.utils.EmailUtils;
import app.utils.PasswordUtils;

import java.io.*;
import java.net.*;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.SecretKey;

public class TcpServer {
    private static final List<ClientHandler> clients = new ArrayList<>();

    private static final Object chatCreationLock = new Object();
    private static final Object registerLock = new Object();

    public static final Gson gson = new Gson();
    public static volatile boolean isServerRunning = true;

    private static final Logger logger = java.util.logging.Logger.getLogger(TcpServer.class.getName());
    private static final Dotenv dotenv = Dotenv.load();

    private static final int TCP_PORT = Integer.parseInt(dotenv.get("TCP_PORT", "25555"));
    private static final int RATE_LIMIT_MINUTES = 15;

    public static void start(){
        try(ServerSocket serverSocket = new ServerSocket(TCP_PORT)){
            serverSocket.setSoTimeout(1000);
            System.out.println("[SERVER] TCP Server listening on port " + TCP_PORT + "...");

            while (isServerRunning){
                try {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("[CONNECTION] Client connected: " + clientSocket.getInetAddress());

                    ClientHandler handler = new ClientHandler(clientSocket);
                    Thread.startVirtualThread(handler);
                }catch (SocketTimeoutException _){}
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "[SERVER] PORT IN USE", e);
        }
    }

    static class ClientHandler implements Runnable{
        private final Socket socket;
        private PrintWriter out;
        private BufferedReader in;

        private User currentUser = null;
        private int currentChatId = -1;
        private boolean isRunning = true;

        private SecretKey sessionKey = null;

        public ClientHandler(Socket socket) {
            this.socket = socket;
            try{
                socket.setTcpNoDelay(true);

                this.out = new PrintWriter(socket.getOutputStream(), true);
                this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            } catch (IOException e) {
                logger.log(Level.SEVERE, "Error initializing I/O streams for client", e);
            }
        }

        @Override
        public void run() {
            try {
                if (!performHandshake()) {
                    System.out.println("[AUTH] Handshake failed.");
                    disconnect();
                    return;
                }

                while (isRunning) {
                    String jsonRequest = in.readLine();
                    if(jsonRequest==null){
                        break;
                    }

                    NetworkPacket packet = NetworkPacket.fromJson(jsonRequest);

                    if (packet.getType() == PacketType.SECURE_ENVELOPE) {
                        try {
                            String encryptedPayload = packet.getPayload().getAsString();
                            byte[] packedBytes = Base64.getDecoder().decode(encryptedPayload);

                            System.out.println("[ENCRYPTED] DATA: " + encryptedPayload);

                            String originalJson = CryptoHelper.unpackAndDecrypt(sessionKey, packedBytes);
                            packet = NetworkPacket.fromJson(originalJson);

                            System.out.println("[DECRYPTED] Original packet: " + originalJson);

                        } catch (Exception e) {
                            System.out.println("[DECRYPTED] Tunnel decryption error!");
                            continue;
                        }
                    }
                    else {
                        System.out.println("[SERVER] Unencrypted packet rejected: " + packet.getType());
                        continue;
                    }

                    switch (packet.getType()) {
                        case LOGIN_REQUEST: handleLogin(packet); break;
                        case REGISTER_REQUEST: handleRegister(packet); break;
                        case CONFIRM_EMAIL_REQUEST: handleConfirmEmail(packet); break;

                        case SEND_MESSAGE: handleSendMessage(packet); break;

                        case GET_CHATS_REQUEST: handleGetChats(); break;
                        case GET_USERS_REQUEST: handleGetUsersForAdd(); break;
                        case CREATE_CHAT_REQUEST: handleCreateChat(packet); break;
                        case DELETE_CHAT_REQUEST: handleDeleteChat(packet); break;
                        case RENAME_CHAT_REQUEST: handleRenameChat(packet); break;
                        case ENTER_CHAT_REQUEST: handleEnterChat(packet); break;
                        case EXIT_CHAT_REQUEST:
                            this.currentChatId = -1;
                            sendPacket(PacketType.EXIT_CHAT_RESPONSE, "BYE");
                            break;

                        case EDIT_MESSAGE_REQUEST: handleEditMessage(packet); break;
                        case DELETE_MESSAGE_REQUEST: handleDeleteMessage(packet); break;
                        case PUBLISH_KEYS:       handlePublishKeys(packet); break;
                        case GET_BUNDLE_REQUEST: handleGetBundle(packet); break;
                        case CALL_REQUEST: handleCallRequest(packet); break;
                        case CALL_ACCEPT:  handleCallAccept(packet); break;
                        case CALL_DENY:    handleCallDeny(packet); break;
                        case CALL_END:     handleCallEnd(packet); break;
                        case GET_CHAT_MEMBERS_REQUEST: handleGetChatMembers(packet); break;
                        case LOGOUT: disconnect(); break;

                        default: System.out.println("Unknown packet: " + packet.getType());
                    }
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Client thread crashed", e);
                disconnect();
            }
        }

        private void handleLogin(NetworkPacket packet) throws IOException {
            ChatDtos.AuthDto dto = gson.fromJson(packet.getPayload(), ChatDtos.AuthDto.class);
            String ip = this.socket.getInetAddress().getHostAddress();

            int failedAttempts = UserLogRepository.countRecentFailedLogins(ip, RATE_LIMIT_MINUTES);
            if (failedAttempts >= 5) {
                UserLogRepository.insert(0, "LOGIN_RATE_LIMITED", System.currentTimeMillis(), ip);
                sendPacket(PacketType.LOGIN_RESPONSE, "RATE_LIMITED");

                return;
            }

            User user = UserRepository.selectUserByEmail(dto.email);

            if (user == null || !PasswordUtils.verifyPassword(dto.password, user.getPasswordHash())) {
                UserLogRepository.insert(0, "LOGIN_FAILED", System.currentTimeMillis(), ip);
                sendPacket(PacketType.LOGIN_RESPONSE, "FAIL");

                return;
            }

            if (!user.isConfirmed()) {
                sendPacket(PacketType.LOGIN_RESPONSE, "NOT_CONFIRMED");
                return;
            }

            synchronized (clients) {
                for (ClientHandler c : clients) {
                    if (c.currentUser != null && c.currentUser.getId() == user.getId()) {
                        c.disconnect();
                        break;
                    }
                }
                clients.add(this);
            }

            this.currentUser = user;
            UserLogRepository.insert(user.getId(), "LOGIN", System.currentTimeMillis(), socket.getInetAddress().getHostAddress());
            sendPacket(PacketType.LOGIN_RESPONSE, user);

            System.out.println("[AUTH] User " + user.getId() + " connected.");

            Thread.startVirtualThread(() -> {
                try {
                    Thread.sleep(200);
                    List<String> missedPackets = OfflineQueueRepository.getAndClearPendingPackets(user.getId());

                    if (!missedPackets.isEmpty()) {
                        System.out.println("[OFFLINE] Delivering " + missedPackets.size() + " missed packets to User " + user.getId());
                        for (String json : missedPackets) {
                            sendDirectPacket(NetworkPacket.fromJson(json));
                            Thread.sleep(20);
                        }
                    }
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error delivering offline packets", e);
                }
            });
        }

        private void handleConfirmEmail(NetworkPacket packet) throws IOException {
            String code = gson.fromJson(packet.getPayload(), String.class);
            if (UserRepository.confirmUser(code)) {
                sendPacket(PacketType.CONFIRM_EMAIL_RESPONSE, "OK");
            } else {
                sendPacket(PacketType.CONFIRM_EMAIL_RESPONSE, "INVALID");
            }
        }

        private void handleSendMessage(NetworkPacket packet) throws IOException {
            Message receivedMsg = gson.fromJson(packet.getPayload(), Message.class);

            if (currentChatId == -1) return;
            if (!GroupChatRepository.isMember(currentChatId, currentUser.getId())) return;

            long timestamp = System.currentTimeMillis();

            int msgId = MessageRepository.insertMessageReturningId(
                    receivedMsg.getContent(),
                    timestamp,
                    currentUser.getId(),
                    currentChatId
            );

            Message fullMsg = new Message(msgId, receivedMsg.getContent(), timestamp, currentUser.getId(), currentChatId);

            broadcastToPartner(currentChatId, PacketType.RECEIVE_MESSAGE, fullMsg);
            sendPacket(PacketType.RECEIVE_MESSAGE, fullMsg);
        }

        private void broadcastToPartner(int chatId, PacketType type, Object payload) {
            List<GroupMember> members = GroupChatRepository.selectGroupMembersByChatId(chatId);

            for (GroupMember m : members) {
                int targetId = m.getUserId();
                if (targetId == currentUser.getId()) continue;

                NetworkPacket p = new NetworkPacket(type, currentUser.getId(), payload);

                synchronized (clients) {
                    for (ClientHandler client : clients) {
                        if (client.currentUser != null && client.currentUser.getId() == targetId) {
                            try {
                                client.sendDirectPacket(p);
                            } catch (IOException e) {logger.log(Level.WARNING, "Failed to broadcast packet to user: " + targetId, e);}
                            break;
                        }
                    }
                }
            }
        }

        private boolean performHandshake() {
            try {
                System.out.println("[HANDSHAKE] Handshake started...");

                KeyPair kyberPair = CryptoHelper.generateKyberKeys();
                KeyPair ecPair = CryptoHelper.generateECKeys();

                PrivateKey tempKyberPrivate = kyberPair.getPrivate();
                byte[] pubBytes = kyberPair.getPublic().getEncoded();

                String pubBase64 = Base64.getEncoder().encodeToString(pubBytes);
                String ecPubBase64 = Base64.getEncoder().encodeToString(ecPair.getPublic().getEncoded());

                String combinedPayload = pubBase64 + ":" + ecPubBase64;

                NetworkPacket hello = new NetworkPacket(PacketType.KYBER_SERVER_HELLO, 0, combinedPayload);
                synchronized (this){
                    out.println(hello.toJson());
                    out.flush();
                }

                String responseJson = in.readLine();
                NetworkPacket response = NetworkPacket.fromJson(responseJson);

                if (response.getType() == PacketType.KYBER_CLIENT_FINISH) {
                    String payload = response.getPayload().getAsString();
                    String[] parts = payload.split(":");

                    byte[] kyberCipherBytes = Base64.getDecoder().decode(parts[0]);
                    byte[] clientECPubBytes = Base64.getDecoder().decode(parts[1]);

                    SecretKey kyberSecret = CryptoHelper.decapsulate(tempKyberPrivate, kyberCipherBytes);

                    PublicKey clientECPub = CryptoHelper.decodeECPublicKey(clientECPubBytes);
                    byte[] ecSecret = CryptoHelper.doECDH(ecPair.getPrivate(), clientECPub);

                    this.sessionKey = CryptoHelper.combineSecrets(ecSecret, kyberSecret.getEncoded());
                    System.out.println("[HANDSHAKE] Tunel OK!");
                    return true;
                }
                return false;
            } catch (Exception e) { return false; }
        }

        private void sendPacket(PacketType type, Object payload) throws IOException {
            int myId = (currentUser != null) ? currentUser.getId() : 0;
            NetworkPacket p = new NetworkPacket(type, myId, payload);
            sendDirectPacket(p);
        }

        private void sendDirectPacket(NetworkPacket p) throws IOException {
            if (sessionKey != null) {
                try {
                    String clearJson = p.toJson();
                    byte[] encryptedBytes = CryptoHelper.encryptAndPack(sessionKey, clearJson);
                    String encryptedBase64 = Base64.getEncoder().encodeToString(encryptedBytes);
                    NetworkPacket envelope = new NetworkPacket(PacketType.SECURE_ENVELOPE, p.getSenderId(), encryptedBase64);
                    synchronized (this) {
                        out.println(envelope.toJson());
                        out.flush();
                        if(out.checkError()){
                            throw new IOException("Socket not responding for writing ");
                        }
                    }
                } catch (Exception e) {
                    throw new IOException("Socket not responding for writing ");
                }
            } else {
                synchronized (this) {
                    out.println(p.toJson());
                    out.flush();
                    if(out.checkError()){
                        throw new IOException("Socket not responding for writing ");
                    }
                }
            }
        }
        
        private void handleRegister(NetworkPacket packet) throws IOException {
            ChatDtos.AuthDto dto = gson.fromJson(packet.getPayload(), ChatDtos.AuthDto.class);

            synchronized (registerLock) {
                if (UserRepository.selectUserByEmail(dto.email) != null) {
                    sendPacket(PacketType.REGISTER_RESPONSE, "EXISTS");
                    return;
                }

                String hash = PasswordUtils.hashPassword(dto.password);
                String token = UserRepository.insertUser(dto.username, dto.email, hash, System.currentTimeMillis());

                if (token == null) {
                    sendPacket(PacketType.REGISTER_RESPONSE, "FAIL");
                    return;
                }

                EmailUtils.sendConfirmation(dto.email, token);
                sendPacket(PacketType.REGISTER_RESPONSE, "CHECK_EMAIL");

            }
        }

        private void handleGetChats() throws IOException {
            if (currentUser != null) {
                sendPacket(PacketType.GET_CHATS_RESPONSE, GroupChatRepository.selectGroupChatsByUserId(currentUser.getId()));
            }
        }

        private void handleGetUsersForAdd() throws IOException {
            List<String> rawUsers = UserRepository.selectUsersAddConversation();
            List<String> filtered = new ArrayList<>();
            for (String u : rawUsers) {
                int uid = Integer.parseInt(u.split(",")[0]);
                if (uid != currentUser.getId() && uid != -1) filtered.add(u);
            }
            sendPacket(PacketType.GET_USERS_RESPONSE, filtered);
        }

        private void handleEnterChat(NetworkPacket packet) throws IOException {
            int chatId = gson.fromJson(packet.getPayload(), Integer.class);

            if (!GroupChatRepository.isMember(chatId, currentUser.getId())) {
                sendPacket(PacketType.ENTER_CHAT_RESPONSE, "DENIED");
                return;
            }

            this.currentChatId = chatId;
            sendPacket(PacketType.ENTER_CHAT_RESPONSE, "OK");
            List<Message> history = MessageRepository.selectMessagesByGroup(chatId);
            sendPacket(PacketType.GET_MESSAGES_RESPONSE, history);
        }

        private void handleCreateChat(NetworkPacket packet) throws IOException {
            ChatDtos.CreateGroupDto dto = gson.fromJson(packet.getPayload(), ChatDtos.CreateGroupDto.class);

            synchronized (chatCreationLock) {
                GroupChat existing = GroupChatRepository.selectChatBetweenUsers(currentUser.getId(), dto.targetUserId);
                if (existing != null) {
                    System.out.println("[GROUP] Chat already exists between " + currentUser.getId() + " and " + dto.targetUserId);
                    sendPacket(PacketType.CREATE_CHAT_BROADCAST, new ChatDtos.NewChatBroadcastDto(existing, null));
                    return;
                }

                GroupChat newChat = GroupChatRepository.insertGroupChatReturningId(dto.groupName);

                if (newChat != null) {
                    GroupChatRepository.insertGroupMember(newChat.getId(), currentUser.getId());
                    GroupChatRepository.insertGroupMember(newChat.getId(), dto.targetUserId);

                    ChatDtos.NewChatBroadcastDto packetForAlice = new ChatDtos.NewChatBroadcastDto(newChat, null);
                    NetworkPacket pAlice = new NetworkPacket(PacketType.CREATE_CHAT_BROADCAST, currentUser.getId(), packetForAlice);
                    sendDirectPacket(pAlice);

                    ChatDtos.NewChatBroadcastDto packetForBob = new ChatDtos.NewChatBroadcastDto(newChat, dto.initialKeyCiphertext);
                    NetworkPacket pBob = new NetworkPacket(PacketType.CREATE_CHAT_BROADCAST, currentUser.getId(), packetForBob);

                    sendToSpecificUser(dto.targetUserId, pBob);

                    System.out.println("[GROUP] Chat " + newChat.getId() + " created. Key routed to User " + dto.targetUserId);
                }
            }
        }

        private void handleRenameChat(NetworkPacket packet) throws IOException {
            ChatDtos.RenameGroupDto dto = gson.fromJson(packet.getPayload(), ChatDtos.RenameGroupDto.class);

            if (!GroupChatRepository.isMember(dto.chatId, currentUser.getId())) return;

            if(GroupChatRepository.updateGroupChatName(dto.chatId, dto.newName)) {
                NetworkPacket broadcastPacket = new NetworkPacket(PacketType.RENAME_CHAT_BROADCAST, currentUser.getId(), dto);

                sendDirectPacket(broadcastPacket);
                broadcastToChatMembers(dto.chatId, PacketType.RENAME_CHAT_BROADCAST, dto);
            }
            else{
                System.out.println("[SERVER] Failed to rename chat " + dto.chatId + " in DB. Broadcast canceled.");
            }
        }

        private void handleDeleteChat(NetworkPacket packet) throws IOException {
            int chatId = gson.fromJson(packet.getPayload(), Integer.class);

            if (!GroupChatRepository.isMember(chatId, currentUser.getId())) return;
            List<GroupMember> members = GroupChatRepository.selectGroupMembersByChatId(chatId);

            if(GroupChatRepository.deleteGroupChatTransactional(chatId)) {
                NetworkPacket broadcastPacket = new NetworkPacket(PacketType.DELETE_CHAT_BROADCAST, currentUser.getId(), chatId);
                sendDirectPacket(broadcastPacket);

                for (GroupMember m : members) {
                    if (m.getUserId() != currentUser.getId()) {
                        sendToSpecificUser(m.getUserId(), broadcastPacket);
                    }
                }
            }
            else{
                System.out.println("[SERVER] Failed to delete chat from DB. Broadcast canceled.");
            }
        }

        private void sendToSpecificUser(int targetUserId, NetworkPacket p) {
            boolean isOnline = false;

            synchronized (clients) {
                for (ClientHandler client : clients) {
                    if (client.currentUser != null && client.currentUser.getId() == targetUserId) {
                        try {
                            client.sendDirectPacket(p);
                            isOnline = true;
                        } catch (IOException e) {
                            logger.log(Level.WARNING, "Error sending to online client: {0}", targetUserId);
                            client.disconnect();
                        }
                        break;
                    }
                }
            }

            if (!isOnline) {
                PacketType type = p.getType();
                if (type == PacketType.CALL_REQUEST ||
                        type == PacketType.CALL_ACCEPT ||
                        type == PacketType.CALL_DENY ||
                        type == PacketType.CALL_END) {

                    System.out.println("[SERVER] User " + targetUserId + " is offline. Dropping VoIP packet ");
                    return;
                }

                System.out.println("[SERVER] User " + targetUserId + " is offline. Saving packet to queue...");
                String packetJson = p.toJson();

                OfflineQueueRepository.insertPendingPacket(targetUserId, packetJson);
            }
        }

        private void broadcastToChatMembers(int chatId, PacketType type, Object payload) {
            List<GroupMember> members = GroupChatRepository.selectGroupMembersByChatId(chatId);
            NetworkPacket p = new NetworkPacket(type, currentUser.getId(), payload);
            for (GroupMember m : members) {
                if (m.getUserId() != currentUser.getId()) {
                    sendToSpecificUser(m.getUserId(), p);
                }
            }
        }

        private void handleEditMessage(NetworkPacket packet) throws IOException {
            ChatDtos.EditMessageDto dto = gson.fromJson(packet.getPayload(), ChatDtos.EditMessageDto.class);

            if (!MessageRepository.isOwner(dto.messageId, currentUser.getId())) return;

            if (MessageRepository.updateMessageById(dto.messageId, dto.newContent)) {
                if (currentChatId != -1) {
                    broadcastToPartner(currentChatId, PacketType.EDIT_MESSAGE_BROADCAST, dto);
                    sendPacket(PacketType.EDIT_MESSAGE_BROADCAST, dto);
                }
            }
        }
        private void handleDeleteMessage(NetworkPacket packet) throws IOException {
            int msgId = gson.fromJson(packet.getPayload(), Integer.class);

            if (!MessageRepository.isOwner(msgId, currentUser.getId())) return;

            if (MessageRepository.deleteMessageById(msgId)) {
                if (currentChatId != -1) {
                    broadcastToPartner(currentChatId, PacketType.DELETE_MESSAGE_BROADCAST, msgId);
                    sendPacket(PacketType.DELETE_MESSAGE_BROADCAST, msgId);
                }
            }
        }

        private void handlePublishKeys(NetworkPacket packet) {
            try {
                ChatDtos.PublishKeysDto dto = gson.fromJson(packet.getPayload(), ChatDtos.PublishKeysDto.class);

                System.out.println("[PGP] User " + currentUser.getId() + " is publishing new keys...");

                boolean success = UserRepository.updateUserKeys(
                        currentUser.getId(),
                        dto.identityKeyPublic,
                        dto.signedPreKeyPublic,
                        dto.signature
                );

                if (success) {
                    System.out.println("[PGP] Keys saved to database for User " + currentUser.getId());
                } else {
                    System.out.println("[ERROR] Error saving keys to database!");
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error publishing keys to database", e);
            }
        }

        private void handleGetBundle(NetworkPacket packet){
            try {
                ChatDtos.GetBundleRequestDto req = gson.fromJson(packet.getPayload(), ChatDtos.GetBundleRequestDto.class);

                System.out.println("[PGP] User " + currentUser.getId() + " requested bundle for User " + req.targetUserId);

                ChatDtos.GetBundleResponseDto bundle = UserRepository.selectUserKeys(req.targetUserId);

                if (bundle != null) {
                    sendPacket(PacketType.GET_BUNDLE_RESPONSE, bundle);
                    System.out.println("[PGP] Bundle sent to User " + currentUser.getId());
                } else {
                    System.out.println("[PGP] Keys not found for User " + req.targetUserId);
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error retrieving key bundle", e);
            }
        }

        private void handleCallRequest(NetworkPacket packet){
            ChatDtos.CallRequestDto dto = gson.fromJson(packet.getPayload(), ChatDtos.CallRequestDto.class);
            System.out.println("[CALL] User " + currentUser.getId() + " is calling " + dto.targetUserId + " on Chat " + dto.chatId);

            NetworkPacket requestPacket = new NetworkPacket(PacketType.CALL_REQUEST, currentUser.getId(), dto);
            sendToSpecificUser(dto.targetUserId, requestPacket);
        }

        private void handleCallAccept(NetworkPacket packet){
            int callerId = gson.fromJson(packet.getPayload(), Integer.class);
            System.out.println("[CALL] User " + currentUser.getId() + " accepted call from " + callerId);

            NetworkPacket acceptPacket = new NetworkPacket(PacketType.CALL_ACCEPT, currentUser.getId(), currentUser.getId());
            sendToSpecificUser(callerId, acceptPacket);
        }

        private void handleCallDeny(NetworkPacket packet){
            int callerId = gson.fromJson(packet.getPayload(), Integer.class);
            System.out.println("[VOICE] User " + currentUser.getId() + " rejected call from " + callerId);

            NetworkPacket denyPacket = new NetworkPacket(PacketType.CALL_DENY, currentUser.getId(), "BUSY");
            sendToSpecificUser(callerId, denyPacket);
        }

        private void handleCallEnd(NetworkPacket packet){
            int partnerId = gson.fromJson(packet.getPayload(), Integer.class);
            System.out.println("[VOICE] Call ended between " + currentUser.getId() + " and " + partnerId);

            NetworkPacket endPacket = new NetworkPacket(PacketType.CALL_END, currentUser.getId(), "END");
            sendToSpecificUser(partnerId, endPacket);

            UdpServer.activeCallers.remove(currentUser.getId());
            UdpServer.activeCallers.remove(partnerId);

            UdpServer.activeVideo.remove(currentUser.getId());
            UdpServer.activeVideo.remove(partnerId);
        }

        private void handleGetChatMembers(NetworkPacket packet) throws IOException {
            int requestedChatId = gson.fromJson(packet.getPayload(), Integer.class);

            if (!GroupChatRepository.isMember(requestedChatId, currentUser.getId())) return;
            List<GroupMember> members = GroupChatRepository.selectGroupMembersByChatId(requestedChatId);

            List<Integer> memberIds = new ArrayList<>();
            for (GroupMember m : members) {
                memberIds.add(m.getUserId());
            }

            sendPacket(PacketType.GET_CHAT_MEMBERS_RESPONSE, memberIds);
        }

        private void disconnect() {
            isRunning = false;
            synchronized (clients) { clients.remove(this); }
            try { socket.close(); } catch (IOException e) {logger.log(Level.WARNING, "Error closing client socket", e);}
            logger.info("Client disconnected.");
        }
    }
}

