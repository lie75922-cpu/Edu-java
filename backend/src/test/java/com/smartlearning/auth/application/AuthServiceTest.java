package com.smartlearning.auth.application;

import com.smartlearning.common.exception.ConflictException;
import com.smartlearning.common.exception.UnauthenticatedException;
import com.smartlearning.user.domain.PlatformUser;
import com.smartlearning.user.domain.Role;
import com.smartlearning.user.infrastructure.persistence.PlatformUserRepository;
import com.smartlearning.user.infrastructure.persistence.RoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private PlatformUserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenService jwtTokenService;

    @Test
    void registerHashesPasswordAndAssignsOnlyStudentRole() {
        Role student = org.mockito.Mockito.mock(Role.class);
        org.mockito.Mockito.when(student.getCode()).thenReturn("STUDENT");
        when(userRepository.existsByUsername("learner")).thenReturn(false);
        when(roleRepository.findByCode("STUDENT")).thenReturn(Optional.of(student));
        when(passwordEncoder.encode("long-enough-password")).thenReturn("bcrypt-value");
        when(userRepository.save(any(PlatformUser.class))).thenAnswer(invocation -> {
            PlatformUser user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", 9L);
            return user;
        });
        when(jwtTokenService.issue(any(PlatformUser.class))).thenReturn(
                new JwtTokenService.IssuedToken("token", Instant.parse("2026-01-01T00:00:00Z"), List.of("STUDENT"))
        );
        AuthService service = new AuthService(userRepository, roleRepository, passwordEncoder, jwtTokenService);

        AuthService.AuthenticationResult result = service.register("learner", "long-enough-password", "Learner");

        ArgumentCaptor<PlatformUser> userCaptor = ArgumentCaptor.forClass(PlatformUser.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo("bcrypt-value");
        assertThat(userCaptor.getValue().getRoles()).containsExactly(student);
        assertThat(result.user().roles()).containsExactly("STUDENT");
    }

    @Test
    void duplicateRegistrationIsRejectedBeforePasswordWork() {
        when(userRepository.existsByUsername("taken")).thenReturn(true);
        AuthService service = new AuthService(userRepository, roleRepository, passwordEncoder, jwtTokenService);

        assertThatThrownBy(() -> service.register("taken", "long-enough-password", "Taken"))
                .isInstanceOf(ConflictException.class);
        org.mockito.Mockito.verifyNoInteractions(passwordEncoder, roleRepository, jwtTokenService);
    }

    @Test
    void loginRejectsDisabledPlatformUser() {
        PlatformUser user = new PlatformUser("disabled", "bcrypt", "Disabled");
        ReflectionTestUtils.setField(user, "status", "DISABLED");
        when(userRepository.findByUsername("disabled")).thenReturn(Optional.of(user));
        AuthService service = new AuthService(userRepository, roleRepository, passwordEncoder, jwtTokenService);

        assertThatThrownBy(() -> service.login("disabled", "long-enough-password"))
                .isInstanceOf(UnauthenticatedException.class);
        org.mockito.Mockito.verifyNoInteractions(jwtTokenService);
    }
}
