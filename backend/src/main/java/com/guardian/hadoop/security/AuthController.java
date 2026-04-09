package com.guardian.hadoop.security;

import java.util.Collections;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final GuardianAuthenticationService authenticationService;
    private final GuardianTokenService tokenService;

    public AuthController(GuardianAuthenticationService authenticationService, GuardianTokenService tokenService) {
        this.authenticationService = authenticationService;
        this.tokenService = tokenService;
    }

    @PostMapping("/login")
    public AuthSessionResponse login(@RequestBody AuthLoginRequest request) {
        GuardianAuthenticatedUser user = authenticationService.authenticate(request.getUsername(), request.getPassword());
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }
        String token = tokenService.issueToken(user);
        return new AuthSessionResponse(token, user.getUsername(), user.getDisplayName(), user.getRole());
    }

    @GetMapping("/me")
    public AuthSessionResponse current(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof GuardianAuthenticatedUser)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "当前未登录");
        }
        GuardianAuthenticatedUser user = (GuardianAuthenticatedUser) authentication.getPrincipal();
        return new AuthSessionResponse("", user.getUsername(), user.getDisplayName(), user.getRole());
    }

    @PostMapping("/logout")
    public Object logout() {
        return Collections.singletonMap("success", Boolean.TRUE);
    }
}
