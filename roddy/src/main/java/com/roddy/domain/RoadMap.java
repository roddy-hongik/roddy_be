package com.roddy.domain;


import com.roddy.domain.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
@Table(name = "roadmaps")
public class RoadMap extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "roadmap_id")
    private Long id;


    @ManyToOne(fetch = FetchType.LAZY,optional = false)
    @JoinColumn(name = "user_id",nullable = false)
    private User user;


    @Column(nullable = false)
    private String title;           // 제목

    @Column(columnDefinition = "TEXT", nullable = false)
    private String basic;           // 기초

    @Column(columnDefinition = "TEXT", nullable = false)
    private String advance;         // 심화

    @Column(columnDefinition = "TEXT", nullable = false)
    private String project;         // 실전

    public static RoadMap create(User user, String title,
                                 String basic, String advance, String project) {
        return RoadMap.builder()
                .user(user)
                .title(title)
                .basic(basic)
                .advance(advance)
                .project(project)
                .build();
    }

    public void update(String title, String basic,
                       String advance, String project) {
        this.title = title;
        this.basic = basic;
        this.advance = advance;
        this.project = project;
    }

}
