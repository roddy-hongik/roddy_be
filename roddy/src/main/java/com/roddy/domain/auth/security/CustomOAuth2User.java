package com.roddy.domain.auth.security;

import com.roddy.domain.auth.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.Map;

public record CustomOAuth2User(
        User user,
        Map<String, Object> attributes,
        Collection<? extends GrantedAuthority> authorities
) implements OAuth2User {

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public String getName() {
        Object sub = attributes.get("sub");
        if (sub != null) {
            return String.valueOf(sub);
        }

        Object id = attributes.get("id");
        if (id != null) {
            return String.valueOf(id);
        }

        return user.getEmail();
    }
}
