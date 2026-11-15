package com.warbox.mail;

/** Outgoing e-mail port. Production uses SMTP; tests replace it with an in-memory recorder. */
public interface EmailSender {

    void send(String to, String subject, String textBody);
}
