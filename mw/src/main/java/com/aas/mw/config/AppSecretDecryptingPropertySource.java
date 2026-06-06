package com.aas.mw.config;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;

public class AppSecretDecryptingPropertySource extends EnumerablePropertySource<PropertySource<?>> {

    private final EnumerablePropertySource<?> delegate;
    private final String masterKey;
    private final Map<String, Object> cache = new ConcurrentHashMap<>();

    public AppSecretDecryptingPropertySource(EnumerablePropertySource<?> delegate, String masterKey) {
        super(delegate.getName(), delegate);
        this.delegate = delegate;
        this.masterKey = masterKey;
    }

    @Override
    public String[] getPropertyNames() {
        return delegate.getPropertyNames();
    }

    @Override
    public Object getProperty(String name) {
        return cache.computeIfAbsent(name, this::resolveProperty);
    }

    private Object resolveProperty(String name) {
        Object value = delegate.getProperty(name);
        if (!(value instanceof String text) || !AppSecretCrypto.isEncryptedValue(text)) {
            return value;
        }
        return AppSecretCrypto.decrypt(text, masterKey);
    }
}
