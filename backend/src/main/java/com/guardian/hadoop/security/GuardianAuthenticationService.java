package com.guardian.hadoop.security;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class GuardianAuthenticationService {

    private final GuardianSecurityProperties properties;

    public GuardianAuthenticationService(GuardianSecurityProperties properties) {
        this.properties = properties;
    }

    public GuardianAuthenticatedUser authenticate(String username, String password) {
        for (GuardianSecurityProperties.User user : configuredUsers()) {
            if (equalsIgnoreCase(user.getUsername(), username) && matchesPassword(password, user.getPassword())) {
                return new GuardianAuthenticatedUser(user.getUsername(), user.getDisplayName(), user.getRole());
            }
        }
        return null;
    }

    private List<GuardianSecurityProperties.User> configuredUsers() {
        List<GuardianSecurityProperties.User> users = new ArrayList<GuardianSecurityProperties.User>();
        if (properties.getAdmin() != null) {
            users.add(properties.getAdmin());
        }
        if (properties.getOperator() != null) {
            users.add(properties.getOperator());
        }
        return users;
    }

    private boolean equalsIgnoreCase(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    private boolean matchesPassword(String raw, String expected) {
        return raw != null && expected != null && raw.equals(expected);
    }
}
