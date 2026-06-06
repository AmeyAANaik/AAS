package com.aas.mw.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;

class AppSecretEnvironmentPostProcessorTest {

    @Test
    void decryptsValuesForValueAndConfigurationProperties() {
        String masterKey = "context-master-key";
        String encryptedJwt = AppSecretCrypto.encrypt("12345678901234567890123456789012", masterKey);
        String encryptedPassword = AppSecretCrypto.encrypt("admin", masterKey);

        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "APP_CONFIG_MASTER_KEY", masterKey,
                "jwt.secret", encryptedJwt,
                "jwt.expiration-seconds", "3600",
                "erp.setup.password", encryptedPassword,
                "erp.setup.full-name", "Administrator")));

        new AppSecretEnvironmentPostProcessor()
                .postProcessEnvironment(environment, new SpringApplication(TestConfig.class));

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.setEnvironment(environment);
            context.register(TestConfig.class);
            context.refresh();

            assertEquals("12345678901234567890123456789012", context.getBean(JwtProperties.class).getSecret());
            assertEquals("admin", context.getBean(ErpSetupProperties.class).getPassword());
            assertEquals("admin", context.getBean(TestValueBean.class).password());
        }
    }

    @Test
    void plaintextStillBindsUnchanged() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "jwt.secret", "12345678901234567890123456789012",
                "jwt.expiration-seconds", "3600",
                "erp.setup.password", "plain-password",
                "erp.setup.full-name", "Administrator")));

        new AppSecretEnvironmentPostProcessor()
                .postProcessEnvironment(environment, new SpringApplication(TestConfig.class));

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.setEnvironment(environment);
            context.register(TestConfig.class);
            context.refresh();

            assertEquals("plain-password", context.getBean(ErpSetupProperties.class).getPassword());
            assertEquals("plain-password", context.getBean(TestValueBean.class).password());
        }
    }

    @Test
    void encryptedValuesWithoutMasterKeyFailStartup() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "jwt.secret", AppSecretCrypto.encrypt("12345678901234567890123456789012", "master-key"),
                "jwt.expiration-seconds", "3600",
                "erp.setup.password", AppSecretCrypto.encrypt("admin", "master-key"),
                "erp.setup.full-name", "Administrator")));

        assertThrows(IllegalStateException.class,
                () -> new AppSecretEnvironmentPostProcessor()
                        .postProcessEnvironment(environment, new SpringApplication(TestConfig.class)));
    }

    @Configuration
    @EnableConfigurationProperties({JwtProperties.class, ErpSetupProperties.class})
    static class TestConfig {
        @Bean
        TestValueBean testValueBean(@Value("${erp.setup.password}") String password) {
            return new TestValueBean(password);
        }
    }

    record TestValueBean(String password) {
    }
}
