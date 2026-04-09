package com.guardian.hadoop.security;

public class AuthSessionResponse {

    private final String token;
    private final String username;
    private final String displayName;
    private final String role;

    public AuthSessionResponse(String token, String username, String displayName, String role) {
        this.token = token;
        this.username = username;
        this.displayName = displayName;
        this.role = role;
    }

    public String getToken() {
        return token;
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
