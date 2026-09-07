package com.smartlearning.auth.application;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import com.smartlearning.common.config.JwtProperties;
import com.smartlearning.user.domain.PlatformUser;
import com.smartlearning.user.domain.Role;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtTokenServiceTest {

    @Test
    void issuesASpringSecurityHmacJwtWithPlatformClaims() {
        JwtProperties properties = new JwtProperties("01234567890123456789012345678901", "https://edu-test.local", 3600);
        SecretKey key = new SecretKeySpec(properties.secret().getBytes(), "HmacSHA256");
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(key));
        JwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        PlatformUser user = new PlatformUser("learner", "encoded", "Learner");
        ReflectionTestUtils.setField(user, "id", 42L);
        Role student = mock(Role.class);
        when(student.getCode()).thenReturn("STUDENT");
        user.addRole(student);

        JwtTokenService.IssuedToken issued = new JwtTokenService(encoder, properties).issue(user);
        Jwt decoded = decoder.decode(issued.token());

        assertThat(decoded.getIssuer().toString()).isEqualTo("https://edu-test.local");
        assertThat(decoded.getClaimAsString("user_id")).isEqualTo("42");
        assertThat(decoded.getClaimAsString("username")).isEqualTo("learner");
        assertThat(decoded.getClaimAsStringList("roles")).isEqualTo(List.of("STUDENT"));
    }
}
