package com.smartlearning.auth.application;

import com.smartlearning.common.exception.ConflictException;
import com.smartlearning.common.exception.NotFoundException;
import com.smartlearning.common.exception.ForbiddenOperationException;
import com.smartlearning.common.exception.UnauthenticatedException;
import com.smartlearning.user.domain.PlatformUser;
import com.smartlearning.user.domain.Role;
import com.smartlearning.user.infrastructure.persistence.PlatformUserRepository;
import com.smartlearning.user.infrastructure.persistence.RoleRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AuthService {

    private final PlatformUserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    public AuthService(
            PlatformUserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
    }

    @Transactional
    public AuthenticationResult register(String username, String password, String nickname) {
        if (userRepository.existsByUsername(username)) {
            throw new ConflictException("username is already registered");
        }
        Role studentRole = roleRepository.findByCode("STUDENT")
                .orElseThrow(() -> new IllegalStateException("STUDENT role is not initialized"));
        PlatformUser user = new PlatformUser(username, passwordEncoder.encode(password), nickname);
        user.addRole(studentRole);
        PlatformUser saved = userRepository.save(user);
        return toAuthenticationResult(saved);
    }

    public AuthenticationResult login(String username, String password) {
        PlatformUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UnauthenticatedException("invalid username or password"));
        if (!"ACTIVE".equals(user.getStatus()) || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new UnauthenticatedException("invalid username or password");
        }
        return toAuthenticationResult(user);
    }

    public UserProfile getProfile(long userId) {
        PlatformUser user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("user does not exist"));
        return toProfile(user);
    }

    private AuthenticationResult toAuthenticationResult(PlatformUser user) {
        JwtTokenService.IssuedToken token = jwtTokenService.issue(user);
        return new AuthenticationResult(token.token(), token.expiresAt().toString(), toProfile(user));
    }

    private UserProfile toProfile(PlatformUser user) {
        List<String> roles = user.getRoles().stream().map(Role::getCode).sorted().toList();
        return new UserProfile(user.getId(), user.getUsername(), user.getNickname(), roles);
    }

    public record AuthenticationResult(String accessToken, String expiresAt, UserProfile user) {
    }

    public record UserProfile(Long id, String username, String nickname, List<String> roles) {
    }
}
