package com.roddy.domain.auth.repository;

import com.roddy.domain.auth.entity.User;
import com.roddy.domain.enums.SocialType;
import com.roddy.domain.enums.DesiredJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByEmailAndDeletedAtIsNull(String email);

    Optional<User> findByIdAndDeletedAtIsNull(Long id);

    Optional<User> findBySocialTypeAndSocialIdAndDeletedAtIsNull(SocialType socialType, String socialId);

    boolean existsByEmail(String email);

    List<User> findAllByDesiredJobAndDeletedAtIsNull(DesiredJob desiredJob);

    Page<User> findAllByDeletedAtIsNull(Pageable pageable);

    /** 로그인 시각만 바꾼다. 엔티티를 고치면 수정 시각까지 바뀌어 프로필을 고친 것처럼 보인다. */
    @Modifying
    @Transactional
    @Query("update User u set u.lastLoginAt = :loggedInAt where u.id = :userId")
    void updateLastLoginAt(@Param("userId") Long userId, @Param("loggedInAt") LocalDateTime loggedInAt);
}
