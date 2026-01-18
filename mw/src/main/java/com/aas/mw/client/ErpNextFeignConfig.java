package com.aas.mw.client;

import com.aas.mw.config.ErpNextProperties;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;

public class ErpNextFeignConfig {

    @Bean
    public RequestInterceptor erpNextAuthInterceptor(ErpNextProperties properties) {
        return template -> {
            String token = String.format("token %s:%s", properties.getApiKey(), properties.getApiSecret());
            template.header("Authorization", token);
        };
    }
}
