
package com.abrar.shopsphere.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class OpenAIConfig {

    @Bean
    public RestClient openAiRestClient() {

        return RestClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .build();
    }
}
