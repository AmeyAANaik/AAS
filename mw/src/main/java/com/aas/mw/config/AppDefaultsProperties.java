package com.aas.mw.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.defaults")
public class AppDefaultsProperties {

    private boolean enabled = true;
    private final UserDefaults vendor = new UserDefaults();
    private final UserDefaults shop = new UserDefaults();
    private final UserDefaults helper = new UserDefaults();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public UserDefaults getVendor() {
        return vendor;
    }

    public UserDefaults getShop() {
        return shop;
    }

    public UserDefaults getHelper() {
        return helper;
    }

    public static class UserDefaults {
        private String email = "";
        private String name = "";
        private String password = "";
        private String supplier = "";
        private String customer = "";

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getSupplier() {
            return supplier;
        }

        public void setSupplier(String supplier) {
            this.supplier = supplier;
        }

        public String getCustomer() {
            return customer;
        }

        public void setCustomer(String customer) {
            this.customer = customer;
        }
    }
}
