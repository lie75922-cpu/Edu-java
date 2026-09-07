package com.smartlearning.auth.application;

import com.smartlearning.common.config.BootstrapAdminProperties;
import com.smartlearning.user.domain.PlatformUser;
import com.smartlearning.user.domain.Role;
import com.smartlearning.user.infrastructure.persistence.PlatformUserRepository;
import com.smartlearning.user.infrastructure.persistence.RoleRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@Configuration
public class BootstrapAdminRunner {

    @Bean
    ApplicationRunner bootstrapSystemAdmin(
            BootstrapAdminProperties properties,
            PlatformUserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder
    ) {
        return args -> createIfConfigured(properties, userRepository, roleRepository, passwordEncoder);
    }

    @Transactional
    void createIfConfigured(
            BootstrapAdminProperties properties,
            PlatformUserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder
    ) {
        if (properties.isPartiallyConfigured()) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_USERNAME and BOOTSTRAP_ADMIN_PASSWORD must be set together");
        }
        if (!properties.isConfigured() || userRepository.existsByUsername(properties.username())) {
            return;
        }
        if (properties.password().length() < 12) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_PASSWORD must be at least 12 characters");
        }
        Role systemAdmin = roleRepository.findByCode("SYSTEM_ADMIN")
                .orElseThrow(() -> new IllegalStateException("SYSTEM_ADMIN role is not initialized"));
        PlatformUser user = new PlatformUser(properties.username(), passwordEncoder.encode(properties.password()), properties.nickname());
        user.addRole(systemAdmin);
        userRepository.save(user);
    }
}
