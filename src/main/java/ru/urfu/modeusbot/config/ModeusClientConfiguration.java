package ru.urfu.modeusbot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class ModeusClientConfiguration {

    @Bean
    RestClient modeusRestClient(RestClient.Builder builder, ModeusProperties properties) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout());
        return builder.baseUrl(properties.baseUrl().toString())
                .requestFactory(factory)
                .build();
    }
}
