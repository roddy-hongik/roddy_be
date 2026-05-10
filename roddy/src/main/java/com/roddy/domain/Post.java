package com.roddy.domain;

import com.roddy.domain.enums.PostCategory;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
@Table(name = "posts")
public class Post extends BaseEntity{

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "post_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PostCategory category;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @ManyToOne(fetch = FetchType.LAZY,optional = false)
    @JoinColumn(name = "user_id",nullable = false)
    private User user; // 작성자

    @Column(nullable = false)
    private int viewCount=0;

    @Column(nullable = false)
    private int likeCount=0;

    @ElementCollection
    @CollectionTable(
            name = "post_image_urls",
            joinColumns = @JoinColumn(name = "post_id")
    )
    @Column(name = "image_url")
    private List<String> postImageUrls = new ArrayList<>();

    public static Post create(PostCategory category, String title,
                              String content, User user) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("제목은 필수입니다.");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("내용은 필수입니다.");
        }

        return Post.builder()
                .category(category)
                .title(title)
                .content(content)
                .user(user)
                .viewCount(0)   // 초기값 보장
                .likeCount(0)
                .postImageUrls(new ArrayList<>())
                .build();
    }

    // 조회수 증가
    public void increaseViewCount() {
        this.viewCount++;
    }

    // 좋아요 증가/감소
    public void increaseLikeCount() {
        this.likeCount++;
    }

    public void decreaseLikeCount() {
        if (this.likeCount > 0) {
            this.likeCount--;
        }
    }

    // 게시글 수정
    public void update(String title, String content, PostCategory category) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("제목은 필수입니다.");
        }

        if (content == null || content.isBlank()) {
                   throw new IllegalArgumentException("내용은 필수입니다.");
              }
          if (category == null) {
              throw new IllegalArgumentException("카테고리는 필수입니다.");
              }
        this.title = title;
        this.content = content;
        this.category = category;
    }
}