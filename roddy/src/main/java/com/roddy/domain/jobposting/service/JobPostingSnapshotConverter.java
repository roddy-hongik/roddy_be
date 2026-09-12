package com.roddy.domain.jobposting.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roddy.domain.enums.RecruitType;
import com.roddy.domain.jobposting.dto.JobPostingSnapshot;
import com.roddy.global.crawler.CrawlRecord;
import com.roddy.global.crawler.spec.CrawlSpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 수집 엔진이 내놓은 레코드를 도메인이 쓰는 스냅샷으로 옮긴다.
 *
 * <p>회사마다 제각각인 표기(날짜 형식, 마감 표현, 경력 구분)를 여기서 흡수한다. 값이 이상하면
 * 그 값만 버리고 공고는 살린다. 다만 지원 URL 처럼 없으면 공고가 쓸모없어지는 값은 예외를 던져
 * 명세가 덜 채워졌다는 사실이 수집 이력에 드러나게 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobPostingSnapshotConverter {

    private static final String FIELD_DESCRIPTION = "description";
    private static final String FIELD_JOB_CATEGORY = "job_category";
    private static final String FIELD_LOCATION = "location";
    private static final String FIELD_CAREER = "career";
    private static final String FIELD_POSTED_AT = "posted_at";
    private static final String FIELD_DEADLINE = "deadline";
    private static final String FIELD_UPDATED_AT = "updated_at";
    private static final String FIELD_IS_CLOSED = "is_closed";

    /** 고용 형태를 담는 필드명이 명세마다 갈린다. */
    private static final List<String> EMPLOYMENT_TYPE_FIELDS = List.of("employee_type", "employment_type");

    private static final List<String> CLOSED_WORDS = List.of("마감", "종료");
    private static final List<String> OPEN_WORDS = List.of("모집", "진행");
    private static final List<String> CLOSED_VALUES = List.of("closed", "close", "end", "ended", "expired", "done");
    private static final List<String> OPEN_VALUES = List.of("open", "opened", "in_progress", "ongoing", "active");

    private static final String RAW_VALUES = "values";
    private static final String RAW_EXTRA = "extra";
    private static final String RAW_DETAIL_ERROR = "detail_error";

    private final ObjectMapper objectMapper;

    public JobPostingSnapshot convert(CrawlSpec spec, CrawlRecord record) {
        String applyUrl = record.text(CrawlRecord.APPLY_URL);
        if (applyUrl == null) {
            throw new IllegalArgumentException(
                    "[%s] 지원 URL 이 없습니다. 수집 명세에 apply_url 필드나 template 을 추가해야 합니다."
                            .formatted(spec.company()));
        }

        LocalDateTime postedAt = CrawledDateParser.parse(record.text(FIELD_POSTED_AT));

        return JobPostingSnapshot.builder()
                .companyCode(spec.company())
                .company(displayName(spec))
                .externalId(record.text(CrawlRecord.JOB_ID))
                .title(record.text(CrawlRecord.TITLE))
                .content(record.text(FIELD_DESCRIPTION))
                .recruitField(record.text(FIELD_JOB_CATEGORY))
                .recruitType(recruitType(record.text(FIELD_CAREER)))
                .location(record.text(FIELD_LOCATION))
                .employmentType(employmentType(record))
                .applyUrl(applyUrl)
                .postedAt(postedAt)
                .deadline(deadline(record, postedAt, spec))
                .sourceUpdatedAt(CrawledDateParser.parse(record.text(FIELD_UPDATED_AT)))
                .closed(closedBySource(record))
                .rawJson(toRawJson(record))
                .build();
    }

    private String displayName(CrawlSpec spec) {
        String nameKo = spec.companyNameKo();
        return (nameKo == null || nameKo.isBlank()) ? spec.company() : nameKo;
    }

    private String employmentType(CrawlRecord record) {
        return EMPLOYMENT_TYPE_FIELDS.stream()
                .map(record::text)
                .filter(value -> value != null)
                .findFirst()
                .orElse(null);
    }

    /**
     * 마감일이 게시일보다 빠르면 마감일을 버린다.
     *
     * <p>여러 회사가 마감일 자리에 형식적인 값(지난 해, 9999년 등)을 넣는다. 둘 중 틀린 쪽은 대개
     * 마감일이라, 공고를 버리는 대신 마감일만 비우고 목록에서 사라지는 것으로 마감을 판정한다.
     */
    private LocalDateTime deadline(CrawlRecord record, LocalDateTime postedAt, CrawlSpec spec) {
        LocalDateTime deadline = CrawledDateParser.parse(record.text(FIELD_DEADLINE));
        if (deadline == null || postedAt == null || !deadline.isBefore(postedAt)) {
            return deadline;
        }

        log.debug("[{}] 마감일이 게시일보다 빨라 버립니다. job_id={} postedAt={} deadline={}",
                spec.company(), record.text(CrawlRecord.JOB_ID), postedAt, deadline);
        return null;
    }

    /** 인턴 / 신입 / 경력. "신입·경력" 처럼 둘 다 걸리면 구분이 없는 것으로 본다. */
    private RecruitType recruitType(String career) {
        if (career == null) {
            return null;
        }

        String value = career.toLowerCase(Locale.ROOT);
        if (value.contains("인턴") || value.contains("intern")) {
            return RecruitType.INTERN;
        }
        // "경력무관" 처럼 무관이 붙으면 경력 여부를 따지지 않는다는 뜻이다.
        if (value.contains("무관")) {
            return null;
        }

        boolean junior = value.contains("신입") || value.contains("entry");
        boolean senior = value.contains("경력") || value.contains("experienced");
        if (junior == senior) {
            return null;
        }
        return junior ? RecruitType.JUNIOR : RecruitType.SENIOR;
    }

    /** 마감 여부는 마감 / 모집중 / 알 수 없음의 3-상태다. 알려주지 않는 회사가 대부분이다. */
    private Boolean closedBySource(CrawlRecord record) {
        Object raw = record.value(FIELD_IS_CLOSED);
        if (raw == null) {
            return null;
        }
        if (raw instanceof Boolean closed) {
            return closed;
        }

        String value = String.valueOf(raw).trim().toLowerCase(Locale.ROOT);
        if (CLOSED_WORDS.stream().anyMatch(value::contains)) {
            return true;
        }
        if (OPEN_WORDS.stream().anyMatch(value::contains)) {
            return false;
        }
        if (CLOSED_VALUES.contains(value)) {
            return true;
        }
        if (OPEN_VALUES.contains(value)) {
            return false;
        }
        return null;
    }

    /** 명세가 바뀌었을 때 재파싱할 수 있도록 수집 원본을 그대로 남긴다. */
    private String toRawJson(CrawlRecord record) {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put(RAW_VALUES, record.values());
        raw.put(RAW_EXTRA, record.extra());
        if (record.hasDetailError()) {
            raw.put(RAW_DETAIL_ERROR, record.detailError());
        }

        try {
            return objectMapper.writeValueAsString(raw);
        } catch (JsonProcessingException e) {
            // 원본 보관은 부가 정보다. 직렬화에 실패해도 공고 자체는 적재한다.
            log.warn("수집 원본을 JSON 으로 남기지 못했습니다. job_id={}", record.text(CrawlRecord.JOB_ID), e);
            return null;
        }
    }
}
