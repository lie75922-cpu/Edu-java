package com.smartlearning.auth.api;

import com.smartlearning.auth.application.AuthService;
import com.smartlearning.auth.domain.CurrentUser;
import com.smartlearning.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/auth/register")
    public ApiResponse<AuthService.AuthenticationResult> register(@Valid @RequestBody AuthApi.RegisterRequest request) {
        return ApiResponse.ok(authService.register(request.username(), request.password(), request.nickname()));
    }

    @PostMapping("/auth/login")
    public ApiResponse<AuthService.AuthenticationResult> login(@Valid @RequestBody AuthApi.LoginRequest request) {
        return ApiResponse.ok(authService.login(request.username(), request.password()));
    }

    @GetMapping("/users/me")
    public ApiResponse<AuthService.UserProfile> me(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(authService.getProfile(CurrentUser.from(jwt).id()));
    }
}
