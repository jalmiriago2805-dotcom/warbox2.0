package com.warbox.mail;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Replaces SMTP with {@link RecordingEmailSender}. Import it next to TestcontainersConfiguration. */
@TestConfiguration(proxyBeanMethods = false)
public class TestMailConfiguration {

    @Bean
    @Primary
    RecordingEmailSender recordingEmailSender() {
        return new RecordingEmailSender();
    }
}
