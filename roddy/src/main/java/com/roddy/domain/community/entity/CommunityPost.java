package com.roddy.domain.community.entity;

import com.roddy.domain.BaseEntity;
import com.roddy.domain.auth.entity.User;
import com.roddy.domain.community.enums.CommunityJobCategory;
import com.roddy.domain.community.enums.CommunityPostCategory;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
@Table(name = "community_posts")
public class CommunityPost extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "community_post_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private CommunityPostCategory postCategory;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private CommunityJobCategory jobCategory;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @ElementCollection
    @CollectionTable(
            name = "community_post_tags",
            joinColumns = @JoinColumn(name = "community_post_id")
    )
    @Column(name = "tag", nullable = false)
    @Builder.Default
    private Set<String> tags = new LinkedHashSet<>();

    @Column(nullable = false)
    private int viewCount;

    @Column(nullable = false)
    private int likeCount;

    @Column(nullable = false)
    private int reportCount;

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<CommunityPostImage> images = new ArrayList<>();

    @OneToOne(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private CommunityRoadmapPostDetail roadmapDetail;

    @OneToOne(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private CommunityInterviewPostDetail interviewDetail;

    public static CommunityPost create(
            User author,
            CommunityPostCategory postCategory,
            CommunityJobCategory jobCategory,
            String title,
            String content,
            List<String> tags
    ) {
        return CommunityPost.builder()
                .author(author)
                .postCategory(postCategory)
                .jobCategory(jobCategory)
                .title(title)
                .content(content)
                .tags(normalizeTags(tags))
                .viewCount(0)
                .likeCount(0)
                .reportCount(0)
                .images(new ArrayList<>())
                .build();
    }

    public void increaseViewCount() {
        this.viewCount++;
    }

    public void increaseLikeCount() {
        this.likeCount++;
    }

    public void decreaseLikeCount() {
        if (this.likeCount > 0) {
            this.likeCount--;
        }
    }

    public void increaseReportCount() {
        this.reportCount++;
    }

    public void addImage(CommunityPostImage image) {
        this.images.add(image);
    }

    public void attachRoadmapDetail(CommunityRoadmapPostDetail roadmapDetail) {
        this.roadmapDetail = roadmapDetail;
    }

    public void attachInterviewDetail(CommunityInterviewPostDetail interviewDetail) {
        this.interviewDetail = interviewDetail;
    }

    public boolean isGeneralPost() {
        return postCategory == CommunityPostCategory.FREE;
    }

    public boolean isRoadmapPost() {
        return postCategory == CommunityPostCategory.ROADMAP;
    }

    public boolean isInterviewPost() {
        return postCategory == CommunityPostCategory.PASS_REVIEW_INTERVIEW;
    }

    private static Set<String> normalizeTags(List<String> tags) {
        if (tags == null) {
            return new LinkedHashSet<>();
        }
        return tags.stream()
                .filter(tag -> tag != null && !tag.isBlank())
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
