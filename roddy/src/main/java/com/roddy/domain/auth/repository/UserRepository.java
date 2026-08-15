package com.roddy.domain.auth.repository;

import com.roddy.domain.auth.entity.User;
import com.roddy.domain.enums.SocialType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByEmailAndDeletedAtIsNull(String email);

    Optional<User> findByIdAndDeletedAtIsNull(Long id);

    Optional<User> findBySocialTypeAndSocialIdAndDeletedAtIsNull(SocialType socialType, String socialId);

    boolean existsByEmail(String email);
}
