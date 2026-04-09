package com.guardian.hadoop.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "guardian.security")
public class GuardianSecurityProperties {

    private boolean enabled = true;
    private String signingSecret = "guardian-dev-secret-change-me";
    private long tokenTtlHours = 12;
    private User admin = defaultAdmin();
    private User operator = defaultOperator();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getSigningSecret() {
        return signingSecret;
    }

    public void setSigningSecret(String signingSecret) {
        this.signingSecret = signingSecret;
    }

    public long getTokenTtlHours() {
        return tokenTtlHours;
    }

    public void setTokenTtlHours(long tokenTtlHours) {
        this.tokenTtlHours = tokenTtlHours;
    }

    public User getAdmin() {
        return admin;
    }

    public void setAdmin(User admin) {
        this.admin = admin;
    }

    public User getOperator() {
        return operator;
    }

    public void setOperator(User operator) {
        this.operator = operator;
    }

    private User defaultAdmin() {
        User user = new User();
        user.setUsername("admin");
        user.setPassword("admin123");
        user.setDisplayName("平台管理员");
        user.setRole("ADMIN");
        return user;
    }

    private User defaultOperator() {
        User user = new User();
        user.setUsername("operator");
        user.setPassword("operator123");
        user.setDisplayName("运维值守");
        user.setRole("OPERATOR");
        return user;
    }

    public static class User {
        private String username;
        private String password;
        private String displayName;
        private String role;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }
    }
}
