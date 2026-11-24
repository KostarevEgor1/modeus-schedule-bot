package ru.urfu.modeusbot.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ScheduleConfiguration {

    @Bean
    Clock scheduleClock(ModeusProperties properties) {
        return Clock.system(properties.zoneId());
    }
}
