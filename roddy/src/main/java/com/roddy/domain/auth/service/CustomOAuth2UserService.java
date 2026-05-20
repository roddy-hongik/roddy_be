package com.roddy.domain.auth.service;

import com.roddy.domain.auth.entity.User;
import com.roddy.domain.auth.security.CustomOAuth2User;
import com.roddy.domain.enums.SocialType;
import com.roddy.domain.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = new DefaultOAuth2UserService().loadUser(userRequest);
        Map<String, Object> attributes = oAuth2User.getAttributes();

        String email = (String) attributes.get("email");
        String name = (String) attributes.getOrDefault("name", email);
        String socialId = (String) attributes.get("sub");
        boolean emailVerified = isEmailVerified(attributes.get("email_verified"));

        if (email == null || socialId == null) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("invalid_user_info"),
                    "구글 사용자 정보 조회에 실패했습니다."
            );
        }

        if (!emailVerified) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("invalid_unverified_email"),
                    "이메일 인증이 완료된 구글 계정만 사용할 수 있습니다."
            );
        }

        User user = userRepository.findByEmail(email)
                .orElseGet(() -> userRepository.save(
                        User.createSocialUser(
                                name,
                                email,
                                createDummyPassword(),
                                SocialType.GOOGLE,
                                socialId
                        )
                ));

        return new CustomOAuth2User(
                user,
                attributes,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
        );
    }

    private String createDummyPassword() {
        return passwordEncoder.encode(UUID.randomUUID().toString());
    }

    private boolean isEmailVerified(Object emailVerified) {
        if (emailVerified instanceof Boolean verified) {
            return verified;
        }
        if (emailVerified instanceof String verified) {
            return Boolean.parseBoolean(verified);
        }
        return false;
    }
}
