package com.roddy.domain;

import com.roddy.domain.enums.DesiredJob;
import com.roddy.domain.enums.ExperienceLevel;
import com.roddy.domain.enums.Role;
import com.roddy.domain.enums.SocialType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "users")
public class User extends BaseEntity{

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;


    @Column(nullable = false, unique = true)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SocialType socialType;


    @Column(nullable = false)
    private String nickname;

    @Column(nullable = false)
    private String username;

    private int age;


    // 경력 연차 (신입/경력 등을 나누기 위해 Enum 사용 권장)
    @Enumerated(EnumType.STRING)
    private ExperienceLevel experienceYears;

    private String socialId;

    private String portfolioUrl;

    private String portfolioFileName;

    private LocalDateTime portfolioUploadedAt;

    private String githubId;
    private String githubUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    private String profileImageUrl;

    private boolean isOnboarded;

    private boolean githubConnected;

    // 희망 직무
    @Enumerated(EnumType.STRING)
    private DesiredJob desiredJob;


    @Builder
    public User(String socialId, String email, SocialType socialType,
                String nickname, String username, Role role) {
        this.socialId = socialId;
        this.email = email;
        this.socialType = socialType;
        this.nickname = nickname;
        this.username = username;
        this.role = role;
        this.isOnboarded = false;
        this.githubConnected = false;
    }
}