package com.warbox.common;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Single source of "now". Business dates (e.g. age checks) are evaluated in America/Sao_Paulo. */
@Configuration(proxyBeanMethods = false)
public class TimeConfiguration {

    public static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    @Bean
    Clock clock() {
        return Clock.system(ZONE);
    }
}
