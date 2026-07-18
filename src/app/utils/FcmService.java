package app.utils;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.cdimascio.dotenv.Dotenv;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;


public class FcmService {
    private static final Logger logger = Logger.getLogger(FcmService.class.getName());
    private static final Gson gson = new Gson();
    private static final HttpClient http = HttpClient.newHttpClient();

    private static boolean initialized = false;

    private static String projectId;
    private static String clientEmail;
    private static PrivateKey privateKey;

    private static String cachedAccessToken;
    private static long tokenExpiresAt = 0;

    public static void init() {
        if (initialized) return;

        try {
            Dotenv dotenv = Dotenv.load();
            String path = dotenv.get("FCM_SERVICE_ACCOUNT_PATH", "firebase-service-account.json");

            String json = Files.readString(Path.of(path));
            JsonObject sa = JsonParser.parseString(json).getAsJsonObject();

            projectId = sa.get("project_id").getAsString();
            clientEmail = sa.get("client_email").getAsString();

            // Parse RSA private key din PEM format
            String pem = sa.get("private_key").getAsString()
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");

            byte[] keyBytes = Base64.getDecoder().decode(pem);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
            privateKey = KeyFactory.getInstance("RSA").generatePrivate(spec);

            initialized = true;
            logger.info("[FCM] Initialized. Project: " + projectId);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "[FCM] Init failed. Push disabled.", e);
        }
    }

    public static void sendPush(String fcmToken, String senderName, int chatId, String type) {
        if (!initialized || fcmToken == null || fcmToken.isEmpty()) return;

        JsonObject data = new JsonObject();
        data.addProperty("type", type);
        data.addProperty("senderName", senderName);
        data.addProperty("chatId", String.valueOf(chatId));

        JsonObject android = new JsonObject();
        android.addProperty("priority", "HIGH");

        sendFcm(fcmToken, data, android);
    }

    public static void sendCallPush(String fcmToken, int callerId, String callerName,
                                    int chatId, boolean isAudio) {
        if (!initialized || fcmToken == null || fcmToken.isEmpty()) return;

        JsonObject data = new JsonObject();
        data.addProperty("type", "INCOMING_CALL");
        data.addProperty("callerId", String.valueOf(callerId));
        data.addProperty("callerName", callerName != null ? callerName : "Unknown");
        data.addProperty("chatId", String.valueOf(chatId));
        data.addProperty("isAudio", String.valueOf(isAudio));

        JsonObject android = new JsonObject();
        android.addProperty("priority", "HIGH");
        android.addProperty("ttl", "30s");

        sendFcm(fcmToken, data, android);
    }

    private static void sendFcm(String fcmToken, JsonObject data, JsonObject androidConfig) {
        Thread.startVirtualThread(() -> {
            try {
                String accessToken = getAccessToken();

                // Construiește payload-ul FCM v1
                // https://firebase.google.com/docs/reference/fcm/rest/v1/projects.messages/send
                JsonObject message = new JsonObject();
                message.addProperty("token", fcmToken);
                message.add("data", data);
                message.add("android", androidConfig);

                JsonObject body = new JsonObject();
                body.add("message", message);

                String url = "https://fcm.googleapis.com/v1/projects/" + projectId + "/messages:send";

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                        .build();

                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    logger.info("[FCM] Push sent OK");
                } else {
                    logger.warning("[FCM] Push failed: " + response.statusCode() + " " + response.body());
                }

            } catch (Exception e) {
                logger.log(Level.WARNING, "[FCM] Send failed: " + e.getMessage());
            }
        });
    }

    private static synchronized String getAccessToken() throws Exception {
        long now = System.currentTimeMillis() / 1000;

        if (cachedAccessToken != null && now < tokenExpiresAt) {
            return cachedAccessToken;
        }

        // 1. Construiește JWT
        String jwt = buildJwt(now);

        // 2. Schimbă JWT pe access token
        String formBody = "grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer"
                + "&assertion=" + jwt;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://oauth2.googleapis.com/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formBody))
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("OAuth2 token request failed: " + response.statusCode() + " " + response.body());
        }

        JsonObject tokenResponse = JsonParser.parseString(response.body()).getAsJsonObject();
        cachedAccessToken = tokenResponse.get("access_token").getAsString();
        tokenExpiresAt = now + 3300; // cache 55 min (token-ul e valid 60 min)

        logger.info("[FCM] OAuth2 token refreshed.");
        return cachedAccessToken;
    }

    /**
     * Construiește un JWT semnat cu RS256 pentru Google OAuth2.
     *
     * Header: {"alg":"RS256","typ":"JWT"}
     * Payload: {
     *   "iss": client_email,
     *   "sub": client_email,
     *   "aud": "https://oauth2.googleapis.com/token",
     *   "iat": now,
     *   "exp": now + 3600,
     *   "scope": "https://www.googleapis.com/auth/firebase.messaging"
     * }
     */
    private static String buildJwt(long now) throws Exception {
        Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();

        // Header
        String header = b64.encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes());

        // Payload
        JsonObject payload = new JsonObject();
        payload.addProperty("iss", clientEmail);
        payload.addProperty("sub", clientEmail);
        payload.addProperty("aud", "https://oauth2.googleapis.com/token");
        payload.addProperty("iat", now);
        payload.addProperty("exp", now + 3600);
        payload.addProperty("scope", "https://www.googleapis.com/auth/firebase.messaging");

        String payloadB64 = b64.encodeToString(payload.toString().getBytes());

        // Sign
        String signingInput = header + "." + payloadB64;

        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(privateKey);
        sig.update(signingInput.getBytes());
        String signature = b64.encodeToString(sig.sign());

        return signingInput + "." + signature;
    }

    private FcmService() {
        throw new UnsupportedOperationException("Utility class");
    }
}
