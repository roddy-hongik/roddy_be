package com.roddy.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 커뮤니티 게시글의 댓글 및 대댓글 정보를 관리하는 엔티티입니다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "comments")
public class Comment extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "comment_id")
    private Long id;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    // optional = false 와 nullable = false 로 필수 연관관계 강제
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Comment parent;

    // 엔티티가 저장/수정되기 전에 데이터 정합성을 검증합니다.
    @PrePersist
    @PreUpdate
    public void validateParentPost() {
        // 객체(Proxy) 비교가 아닌, 실제 식별자(ID) 값을 비교하도록 수정
        if (parent != null && !parent.getPost().getId().equals(this.post.getId())) {
            throw new IllegalStateException("대댓글은 부모 댓글과 같은 게시글에만 작성할 수 있습니다.");
        }
    }
}