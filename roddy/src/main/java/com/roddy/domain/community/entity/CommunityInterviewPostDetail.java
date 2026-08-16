package com.roddy.domain.community.entity;

import com.roddy.domain.community.enums.CommunityInterviewSubtype;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
@Table(name = "community_interview_post_details")
public class CommunityInterviewPostDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "community_interview_post_detail_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "community_post_id", nullable = false, unique = true)
    private CommunityPost post;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CommunityInterviewSubtype subtype;

    @Column(nullable = false)
    private String company;

    @Column(nullable = false)
    private String jobRole;

    @Column(nullable = false)
    private String preparationPeriod;

    @ElementCollection
    @CollectionTable(
            name = "community_interview_post_tech_stacks",
            joinColumns = @JoinColumn(name = "community_interview_post_detail_id")
    )
    @Column(name = "tech_stack", nullable = false)
    @Builder.Default
    private Set<String> techStacks = new LinkedHashSet<>();

    @Column(columnDefinition = "TEXT", nullable = false)
    private String processSummary;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String background;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String preparationProcess;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String experienceDetail;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String advice;

    public static CommunityInterviewPostDetail create(
            CommunityPost post,
            CommunityInterviewSubtype subtype,
            String company,
            String jobRole,
            String preparationPeriod,
            List<String> techStacks,
            String processSummary,
            String background,
            String preparationProcess,
            String experienceDetail,
            String advice
    ) {
        return CommunityInterviewPostDetail.builder()
                .post(post)
                .subtype(subtype)
                .company(requireText(company))
                .jobRole(requireText(jobRole))
                .preparationPeriod(defaultIfBlank(preparationPeriod, "미입력"))
                .techStacks(normalizeValues(techStacks))
                .processSummary(defaultIfBlank(processSummary, ""))
                .background(defaultIfBlank(background, ""))
                .preparationProcess(defaultIfBlank(preparationProcess, ""))
                .experienceDetail(defaultIfBlank(experienceDetail, ""))
                .advice(defaultIfBlank(advice, ""))
                .build();
    }

    private static String requireText(String value) {
        String trimmed = defaultIfBlank(value, null);
        if (trimmed == null) {
            throw new IllegalArgumentException("필수 인터뷰 상세 값이 누락되었습니다.");
        }
        return trimmed;
    }

    private static String defaultIfBlank(String value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? defaultValue : trimmed;
    }

    private static Set<String> normalizeValues(List<String> values) {
        if (values == null) {
            return new LinkedHashSet<>();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
