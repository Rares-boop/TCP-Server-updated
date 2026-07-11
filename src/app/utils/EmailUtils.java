package app.utils;

import io.github.cdimascio.dotenv.Dotenv;

import javax.mail.*;
import javax.mail.internet.*;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public class EmailUtils {
    private static final Logger logger = Logger.getLogger(EmailUtils.class.getName());
    private static final Dotenv dotenv = Dotenv.load();

    private static final String SMTP_EMAIL = dotenv.get("SMTP_EMAIL");
    private static final String SMTP_PASSWORD = dotenv.get("SMTP_PASSWORD");

    public static boolean sendConfirmation(String toEmail, String code) {
        String subject = "TCPSecure — Cod de confirmare";
        String body = "Codul tau de confirmare: " + code;
        return sendEmail(toEmail, subject, body);
    }

    private static boolean sendEmail(String to, String subject, String body) {
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.port", "587");

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(SMTP_EMAIL, SMTP_PASSWORD);
            }
        });

        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(SMTP_EMAIL));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            message.setSubject(subject);
            message.setText(body);

            Transport.send(message);
            logger.info("[SMTP] Confirmation email sent to " + to);
            return true;

        } catch (MessagingException e) {
            logger.log(Level.SEVERE, "[SMTP] Failed to send email to " + to, e);
            return false;
        }
    }

    private EmailUtils() {
        throw new UnsupportedOperationException("Utility class");
    }
}
