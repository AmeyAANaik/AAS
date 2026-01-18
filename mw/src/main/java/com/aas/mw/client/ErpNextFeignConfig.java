package com.aas.mw.client;

import feign.RequestInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.aas.mw.service.ErpSessionStore;

public class ErpNextFeignConfig {

    @Bean
    public RequestInterceptor erpNextAuthInterceptor() {
        return template -> {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) {
                return;
            }
            HttpServletRequest request = attrs.getRequest();
            Object cookie = request.getAttribute(ErpSessionStore.REQUEST_ATTR);
            if (cookie instanceof String sessionCookie && !sessionCookie.isBlank()) {
                template.header("Cookie", sessionCookie);
            }
        };
    }
}
