package com.warbox.mail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** In-memory {@link EmailSender} for tests: nothing leaves the JVM. */
public class RecordingEmailSender implements EmailSender {

    private static final Pattern TOKEN = Pattern.compile("token=([A-Za-z0-9_-]+)");

    public record SentEmail(String to, String subject, String body) {}

    private final List<SentEmail> sent = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void send(String to, String subject, String textBody) {
        sent.add(new SentEmail(to, subject, textBody));
    }

    public List<SentEmail> sent() {
        synchronized (sent) {
            return List.copyOf(sent);
        }
    }

    public SentEmail last() {
        List<SentEmail> all = sent();
        if (all.isEmpty()) {
            throw new AssertionError("No e-mail was sent");
        }
        return all.get(all.size() - 1);
    }

    public void clear() {
        sent.clear();
    }

    public static String extractToken(SentEmail email) {
        Matcher matcher = TOKEN.matcher(email.body());
        if (!matcher.find()) {
            throw new AssertionError("No token link in e-mail: " + email.body());
        }
        return matcher.group(1);
    }
}
