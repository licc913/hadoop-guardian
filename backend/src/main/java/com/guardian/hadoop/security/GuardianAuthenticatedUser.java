package com.guardian.hadoop.security;

public class GuardianAuthenticatedUser {

    private final String username;
    private final String displayName;
    private final String role;

    public GuardianAuthenticatedUser(String username, String displayName, String role) {
        this.username = username;
        this.displayName = displayName;
        this.role = role;
    }

    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getRole() {
        return role;
    }
}
