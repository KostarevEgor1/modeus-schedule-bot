package ru.urfu.modeusbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ModeusScheduleBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(ModeusScheduleBotApplication.class, args);
    }
}
