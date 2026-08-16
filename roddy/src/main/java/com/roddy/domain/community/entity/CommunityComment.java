package com.roddy.domain.community.entity;

import com.roddy.domain.BaseEntity;
import com.roddy.domain.auth.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
@Table(name = "community_comments")
public class CommunityComment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "community_comment_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "community_post_id", nullable = false)
    private CommunityPost post;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_comment_id")
    private CommunityComment parentComment;

    @OneToMany(mappedBy = "parentComment", cascade = jakarta.persistence.CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<CommunityComment> replies = new ArrayList<>();

    public static CommunityComment create(CommunityPost post, User author, String content, CommunityComment parentComment) {
        if (parentComment != null && !parentComment.getPost().getId().equals(post.getId())) {
            throw new IllegalArgumentException("대댓글의 부모 댓글은 같은 게시글에 속해야 합니다.");
        }

        return CommunityComment.builder()
                .post(post)
                .author(author)
                .content(content)
                .parentComment(parentComment)
                .replies(new ArrayList<>())
                .build();
    }

    public boolean isReply() {
        return parentComment != null;
    }

    public boolean isAuthor(Long userId) {
        return author != null && author.getId().equals(userId);
    }

    public int getDepth() {
        return isReply() ? 1 : 0;
    }
}
