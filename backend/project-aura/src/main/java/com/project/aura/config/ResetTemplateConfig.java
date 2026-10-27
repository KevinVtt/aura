package com.project.aura.config;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class ResetTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        // Sin timeout, un pedido a la IA caida queda colgado para siempre y se
        // lleva puesto el hilo del backend.
        factory.setConnectTimeout(Duration.ofSeconds(5));
        // Leer tarda: la IA procesa el PDF entero antes de contestar.
        factory.setReadTimeout(Duration.ofMinutes(10));
        return new RestTemplate(factory);
    }
}
