package com.warbox.account;

import com.warbox.mail.EmailSender;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Builds the pt-BR account e-mails. Links point to frontend routes. */
@Component
public class AccountMailer {

    static final String VERIFICATION_SUBJECT = "Confirme seu e-mail no Warbox";
    static final String PASSWORD_RESET_SUBJECT = "Redefinição de senha do Warbox";

    private final EmailSender emailSender;
    private final AuthProperties properties;
    private final String appBaseUrl;

    public AccountMailer(EmailSender emailSender, AuthProperties properties,
            @Value("${warbox.app-base-url}") String appBaseUrl) {
        this.emailSender = emailSender;
        this.properties = properties;
        this.appBaseUrl = appBaseUrl.endsWith("/") ? appBaseUrl.substring(0, appBaseUrl.length() - 1) : appBaseUrl;
    }

    public void sendVerification(User user, String rawToken) {
        String link = appBaseUrl + "/verificar-email?token=" + rawToken;
        String body = """
                Olá, %s!

                Para confirmar seu e-mail no Warbox, acesse o link abaixo:

                %s

                Este link é válido por %s e só pode ser usado uma vez.

                Se você não criou uma conta no Warbox, ignore esta mensagem.
                """.formatted(user.getName(), link, describe(properties.emailVerificationTtl()));
        emailSender.send(user.getEmail(), VERIFICATION_SUBJECT, body);
    }

    public void sendPasswordReset(User user, String rawToken) {
        String link = appBaseUrl + "/redefinir-senha?token=" + rawToken;
        String body = """
                Olá, %s!

                Recebemos um pedido para redefinir a senha da sua conta no Warbox. Para criar uma nova senha, acesse o link abaixo:

                %s

                Este link é válido por %s e só pode ser usado uma vez.

                Se você não pediu a redefinição, ignore esta mensagem: sua senha continua a mesma.
                """.formatted(user.getName(), link, describe(properties.passwordResetTtl()));
        emailSender.send(user.getEmail(), PASSWORD_RESET_SUBJECT, body);
    }

    static String describe(Duration ttl) {
        long hours = ttl.toHours();
        if (hours >= 1 && ttl.equals(Duration.ofHours(hours))) {
            return hours == 1 ? "1 hora" : hours + " horas";
        }
        long minutes = Math.max(1, ttl.toMinutes());
        return minutes == 1 ? "1 minuto" : minutes + " minutos";
    }
}
