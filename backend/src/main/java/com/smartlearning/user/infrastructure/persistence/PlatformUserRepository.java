package com.smartlearning.user.infrastructure.persistence;

import com.smartlearning.user.domain.PlatformUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlatformUserRepository extends JpaRepository<PlatformUser, Long> {

    Optional<PlatformUser> findByUsername(String username);

    boolean existsByUsername(String username);
}
