package com.roddy.domain.auth.service;

import com.roddy.domain.auth.entity.User;
import com.roddy.domain.auth.repository.UserRepository;
import com.roddy.domain.enums.SocialType;
import com.roddy.global.apiPayload.code.GeneralErrorCode;
import com.roddy.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SocialUserAccountService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User resolveOrCreateSocialUser(
            SocialType socialType,
            String socialId,
            String email,
            String name
    ) {
        return userRepository.findBySocialTypeAndSocialIdAndDeletedAtIsNull(socialType, socialId)
                .orElseGet(() -> userRepository.findByEmailAndDeletedAtIsNull(email)
                        .map(existingUser -> validateExistingSocialUser(existingUser, socialType, socialId))
                        .orElseGet(() -> createSocialUser(socialType, socialId, email, name)));
    }

    private User validateExistingSocialUser(User existingUser, SocialType socialType, String socialId) {
        if (existingUser.getSocialType() != socialType) {
            if (existingUser.getSocialType() == SocialType.LOCAL) {
                throw new GeneralException(
                        GeneralErrorCode.INVALID_PARAMETER,
                        "이미 이메일/비밀번호로 가입된 계정입니다. 로컬 로그인을 이용해주세요."
                );
            }

            throw new GeneralException(
                    GeneralErrorCode.INVALID_PARAMETER,
                    "이미 " + toKoreanProviderName(existingUser.getSocialType()) + " 계정으로 가입된 이메일입니다. 해당 소셜 로그인을 이용해주세요."
            );
        }

        if (!hasText(existingUser.getSocialId())) {
            existingUser.linkSocialId(socialId);
            try {
                return userRepository.saveAndFlush(existingUser);
            } catch (DataIntegrityViolationException exception) {
                return recoverExistingSocialUser(socialType, socialId, existingUser.getEmail());
            }
        }

        if (!existingUser.getSocialId().equals(socialId)) {
            throw new GeneralException(
                    GeneralErrorCode.INVALID_PARAMETER,
                    "이미 다른 " + toKoreanProviderName(socialType) + " 계정으로 가입된 이메일입니다. 해당 소셜 로그인을 이용해주세요."
            );
        }

        return existingUser;
    }

    private User createSocialUser(SocialType socialType, String socialId, String email, String name) {
        try {
            return userRepository.saveAndFlush(
                    User.createSocialUser(
                            name,
                            email,
                            createDummyPassword(),
                            socialType,
                            socialId
                    )
            );
        } catch (DataIntegrityViolationException exception) {
            return recoverExistingSocialUser(socialType, socialId, email);
        }
    }

    private User recoverExistingSocialUser(SocialType socialType, String socialId, String email) {
        return userRepository.findBySocialTypeAndSocialIdAndDeletedAtIsNull(socialType, socialId)
                .orElseGet(() -> userRepository.findByEmailAndDeletedAtIsNull(email)
                        .map(existingUser -> validateRecoveredUser(existingUser, socialType, socialId))
                        .orElseThrow(() -> new GeneralException(GeneralErrorCode.INTERNAL_SERVER_ERROR)));
    }

    private User validateRecoveredUser(User existingUser, SocialType socialType, String socialId) {
        if (existingUser.getSocialType() != socialType) {
            if (existingUser.getSocialType() == SocialType.LOCAL) {
                throw new GeneralException(
                        GeneralErrorCode.INVALID_PARAMETER,
                        "이미 이메일/비밀번호로 가입된 계정입니다. 로컬 로그인을 이용해주세요."
                );
            }

            throw new GeneralException(
                    GeneralErrorCode.INVALID_PARAMETER,
                    "이미 " + toKoreanProviderName(existingUser.getSocialType()) + " 계정으로 가입된 이메일입니다. 해당 소셜 로그인을 이용해주세요."
            );
        }

        if (!hasText(existingUser.getSocialId()) || !existingUser.getSocialId().equals(socialId)) {
            throw new GeneralException(
                    GeneralErrorCode.INVALID_PARAMETER,
                    "이미 다른 " + toKoreanProviderName(socialType) + " 계정으로 가입된 이메일입니다. 해당 소셜 로그인을 이용해주세요."
            );
        }

        return existingUser;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String createDummyPassword() {
        return passwordEncoder.encode(UUID.randomUUID().toString());
    }

    private String toKoreanProviderName(SocialType socialType) {
        return switch (socialType) {
            case GOOGLE -> "구글";
            case KAKAO -> "카카오";
            case LOCAL -> "로컬";
        };
    }
}
