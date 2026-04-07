package com.roddy.domain;

import com.roddy.domain.enums.ExperienceLevel;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "users")
public class User extends BaseEntity{

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Column(nullable = false, unique = true)
    private String loginId;

    @Column(nullable = false, unique = true)
    private String email;

    private String nickname;
    private String userName;
    private String phone;
    private String profileImageUrl;
    private Integer age;
    private String portfolioUrl;

    // 경력 연차 (신입/경력 등을 나누기 위해 Enum 사용 권장)
    @Enumerated(EnumType.STRING)
    private ExperienceLevel experienceYears;

    private String githubUrl;

    // 소셜 로그인 제공자 (GOOGLE, KAKAO 등)
    private String socialProvider;

    // 희망 직무
    private String targetField;

    @Builder
    public User(String loginId, String nickname, String email, ExperienceLevel experienceYears) {
        this.loginId = loginId;
        this.nickname = nickname;
        this.email = email;
        this.experienceYears = experienceYears;
    }
}