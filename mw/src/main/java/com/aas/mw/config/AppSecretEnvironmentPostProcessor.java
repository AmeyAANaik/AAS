package com.aas.mw.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;

public class AppSecretEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String masterKey = environment.getProperty(AppSecretCrypto.ENV_MASTER_KEY);
        boolean hasEncryptedValues = containsEncryptedValues(environment.getPropertySources());
        if (hasEncryptedValues && (masterKey == null || masterKey.isBlank())) {
            throw new IllegalStateException("Encrypted application secrets detected but "
                    + AppSecretCrypto.ENV_MASTER_KEY + " is not set.");
        }
        if (!hasEncryptedValues) {
            return;
        }
        MutablePropertySources sources = environment.getPropertySources();
        for (PropertySource<?> source : sources) {
            if (source instanceof AppSecretDecryptingPropertySource) {
                continue;
            }
            if (source instanceof EnumerablePropertySource<?> enumerable) {
                sources.replace(source.getName(), new AppSecretDecryptingPropertySource(enumerable, masterKey));
            }
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    private boolean containsEncryptedValues(MutablePropertySources sources) {
        for (PropertySource<?> source : sources) {
            if (!(source instanceof EnumerablePropertySource<?> enumerable)) {
                continue;
            }
            for (String propertyName : enumerable.getPropertyNames()) {
                Object value = enumerable.getProperty(propertyName);
                if (value instanceof String text && AppSecretCrypto.isEncryptedValue(text)) {
                    return true;
                }
            }
        }
        return false;
    }
}
