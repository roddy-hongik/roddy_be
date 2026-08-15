package com.roddy.domain.auth.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.roddy.domain.BaseEntity;
import com.roddy.domain.enums.DesiredJob;
import com.roddy.domain.enums.ExperienceLevel;
import com.roddy.domain.enums.Role;
import com.roddy.domain.enums.SocialType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "users",
        indexes = {
                @Index(name = "idx_users_social_type_social_id", columnList = "social_type, social_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_users_social_type_social_id", columnNames = {"social_type", "social_id"})
        }
)
public class User extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;


    @Column(nullable = false, unique = true)
    private String email;

    @NotNull
    @Column(nullable = false)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SocialType socialType;


    @Column(nullable = false)
    private String nickname;

    @Column(nullable = false)
    private String username;

    private int age;

    private String profileImageUrl;

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

    private boolean isOnboarded;

    private boolean githubConnected;

    private LocalDateTime deletedAt;

    // 희망 직무
    @Enumerated(EnumType.STRING)
    private DesiredJob desiredJob;


    @Builder
    public User(String socialId, String email, String password, SocialType socialType,
                String nickname, String username, Role role) {
        this.socialId = socialId;
        this.email = email;
        this.password = password;
        this.socialType = socialType;
        this.nickname = nickname;
        this.username = username;
        this.role = role;
        this.isOnboarded = false;
        this.githubConnected = false;
    }

    public static User signup(String name, String email, String encodedPassword) {
        return User.builder()
                .email(email)
                .password(encodedPassword)
                .socialType(SocialType.LOCAL)
                .nickname(name)
                .username(name)
                .role(Role.USER)
                .build();
    }

    public static User createSocialUser(
            String name,
            String email,
            String encodedPassword,
            SocialType socialType,
            String socialId
    ) {
        return User.builder()
                .socialId(socialId)
                .email(email)
                .password(encodedPassword)
                .socialType(socialType)
                .nickname(name)
                .username(name)
                .role(Role.USER)
                .build();
    }

    public void connectGithub(String githubId, String githubUrl) {
        this.githubId = githubId;
        this.githubUrl = githubUrl;
        this.githubConnected = true;
    }

    public void completeProfile(
            String name,
            int age,
            ExperienceLevel experienceYears,
            DesiredJob desiredJob,
            String portfolioUrl,
            String portfolioFileName,
            LocalDateTime portfolioUploadedAt
    ) {
        this.nickname = name;
        this.username = name;
        this.age = age;
        this.experienceYears = experienceYears;
        this.desiredJob = desiredJob;
        this.portfolioUrl = portfolioUrl;
        this.portfolioFileName = portfolioFileName;
        this.portfolioUploadedAt = portfolioUploadedAt;
        this.isOnboarded = true;
    }

    public void updateMyPageProfile(String name, Integer age, String profileImageUrl) {
        this.nickname = name;
        this.username = name;
        if (age != null) {
            this.age = age;
        }
        this.profileImageUrl = profileImageUrl;
    }

    public void linkSocialId(String socialId) {
        this.socialId = socialId;
    }

    public void withdraw() {
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isWithdrawn() {
        return deletedAt != null;
    }
}
