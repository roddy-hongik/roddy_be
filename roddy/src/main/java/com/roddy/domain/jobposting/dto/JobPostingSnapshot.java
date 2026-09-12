package com.roddy.domain.jobposting.dto;

import com.roddy.domain.enums.DesiredJob;
import com.roddy.domain.enums.RecruitType;
import lombok.Builder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

/**
 * 수집 엔진이 넘겨주는 채용공고 한 건의 정규화된 스냅샷.
 *
 * <p>수집 원본(회사별 YAML 명세로 뽑은 레코드)과 {@link com.roddy.domain.jobposting.entity.JobPosting}
 * 사이의 경계 타입이다. 회사별 응답 형태를 흡수하는 책임은 수집 엔진에, 값 검증은 이 타입에 둔다.
 */
@Builder
public record JobPostingSnapshot(
        String companyCode,
        String company,
        String externalId,
        String title,
        String content,
        String recruitField,
        DesiredJob desiredJob,
        RecruitType recruitType,
        String location,
        String employmentType,
        String applyUrl,
        LocalDateTime postedAt,
        LocalDateTime deadline,
        LocalDateTime sourceUpdatedAt,
        Boolean closed,
        String rawJson
) {

    private static final String HASH_ALGORITHM = "SHA-256";
    private static final String HASH_DELIMITER = "";

    public JobPostingSnapshot {
        requireText(companyCode, "companyCode");
        requireText(company, "company");
        requireText(externalId, "externalId");
        requireText(title, "title");
        requireText(applyUrl, "applyUrl");

        if (postedAt != null && deadline != null && deadline.isBefore(postedAt)) {
            throw new IllegalArgumentException(
                    "마감일은 게시일보다 이후여야 합니다. postedAt=%s, deadline=%s".formatted(postedAt, deadline));
        }
    }

    /**
     * 본문 변경 감지용 해시. 이전 수집과 값이 같으면 상세 재수집과 갱신을 건너뛸 수 있다.
     * 수집할 때마다 달라지는 값(크롤링 시각 등)은 해시 대상에서 제외한다.
     */
    public String contentHash() {
        String source = String.join(HASH_DELIMITER,
                nullToEmpty(title),
                nullToEmpty(content),
                nullToEmpty(recruitField),
                nullToEmpty(location),
                nullToEmpty(employmentType),
                nullToEmpty(applyUrl),
                String.valueOf(deadline));

        try {
            byte[] digest = MessageDigest.getInstance(HASH_ALGORITHM)
                    .digest(source.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("%s 알고리즘을 사용할 수 없습니다.".formatted(HASH_ALGORITHM), e);
        }
    }

    /** 수집 원본이 마감 여부를 알려주지 않는 회사가 많아, 마감 판정은 3-상태(마감/모집중/알 수 없음)로 다룬다. */
    public boolean isClosedBySource() {
        return Boolean.TRUE.equals(closed);
    }

    public boolean isOpenBySource() {
        return Boolean.FALSE.equals(closed);
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("%s 값이 비어 있습니다.".formatted(fieldName));
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
