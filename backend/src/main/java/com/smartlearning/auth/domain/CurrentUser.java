package com.smartlearning.auth.domain;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

public record CurrentUser(Long id, String username, Set<String> roles) {

    public static CurrentUser from(Jwt jwt) {
        Object roleClaim = jwt.getClaim("roles");
        Set<String> roleCodes = new LinkedHashSet<>();
        if (roleClaim instanceof Collection<?> values) {
            values.stream().map(String::valueOf).forEach(roleCodes::add);
        }
        return new CurrentUser(Long.parseLong(jwt.getClaimAsString("user_id")), jwt.getClaimAsString("username"), Set.copyOf(roleCodes));
    }

    public boolean hasAnyRole(String... expectedRoles) {
        for (String expectedRole : expectedRoles) {
            if (roles.contains(expectedRole)) {
                return true;
            }
        }
        return false;
    }
}
