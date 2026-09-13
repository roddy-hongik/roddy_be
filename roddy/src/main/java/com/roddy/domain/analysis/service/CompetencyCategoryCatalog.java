package com.roddy.domain.analysis.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.roddy.domain.analysis.dto.CompetencyCategory;
import com.roddy.domain.enums.DesiredJob;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 직무별 역량 분석 평가 축.
 *
 * <p>축은 백엔드가 정해 AI 서버에 넘긴다. LLM 이 매번 축을 새로 지으면 리포트끼리 점수를 견줄 수 없기
 * 때문이다. 축을 늘리거나 문구를 고칠 때 코드를 고치지 않도록 YAML 로 관리한다.
 *
 * <p>읽을 때 형식을 검사하고, 틀리면 기동을 멈춘다. 잘못 적은 축이 조용히 리포트에 섞이면 되돌리기 어렵다.
 */
@Component
public class CompetencyCategoryCatalog {

    private static final String CATALOG_PATH = "analysis/competency-categories.yaml";

    /** 리포트에 저장하는 값이라 형식을 묶어 둔다. */
    private static final Pattern CODE_PATTERN = Pattern.compile("[A-Z][A-Z0-9_]*");

    private final Map<DesiredJob, List<CompetencyCategory>> categoriesByJob;

    public CompetencyCategoryCatalog() {
        this(CATALOG_PATH);
    }

    CompetencyCategoryCatalog(String path) {
        this.categoriesByJob = load(path);
    }

    /** 축을 정하지 않은 직무거나 직무가 비어 있으면 빈 목록이다. 그러면 축 없이 분석한다. */
    public List<CompetencyCategory> categoriesOf(DesiredJob desiredJob) {
        if (desiredJob == null) {
            return List.of();
        }
        return categoriesByJob.getOrDefault(desiredJob, List.of());
    }

    private Map<DesiredJob, List<CompetencyCategory>> load(String path) {
        Map<DesiredJob, List<CompetencyCategory>> loaded = new EnumMap<>(DesiredJob.class);
        read(path).jobs().forEach((jobName, categories) -> {
            DesiredJob desiredJob = parseJob(path, jobName);
            validate(path, desiredJob, categories);
            loaded.put(desiredJob, List.copyOf(categories));
        });
        return Collections.unmodifiableMap(loaded);
    }

    private Catalog read(String path) {
        ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return yamlMapper.readValue(in, Catalog.class);
        } catch (IOException e) {
            throw new IllegalStateException("역량 평가 축을 읽지 못했습니다: %s".formatted(path), e);
        }
    }

    /** 예전 직무 값(BEGINNER 등)은 숙련도였지 직무가 아니라 축을 둘 수 없다. */
    private DesiredJob parseJob(String path, String jobName) {
        try {
            DesiredJob desiredJob = DesiredJob.valueOf(jobName);
            if (!desiredJob.isLegacyValue()) {
                return desiredJob;
            }
        } catch (IllegalArgumentException ignored) {
            // 모르는 값도 예전 값과 같이 아래에서 알린다.
        }
        throw new IllegalStateException("%s: 축을 둘 수 없는 직무입니다: %s".formatted(path, jobName));
    }

    private void validate(String path, DesiredJob desiredJob, List<CompetencyCategory> categories) {
        if (categories == null || categories.isEmpty()) {
            throw new IllegalStateException(
                    "%s: %s 에 축이 없습니다. 축을 정하지 않은 직무는 적지 않습니다.".formatted(path, desiredJob));
        }

        Set<String> codes = new HashSet<>();
        for (CompetencyCategory category : categories) {
            if (category.code() == null || !CODE_PATTERN.matcher(category.code()).matches()) {
                throw new IllegalStateException(
                        "%s: %s 의 code 형식이 잘못됐습니다: %s".formatted(path, desiredJob, category.code()));
            }
            if (isBlank(category.name()) || isBlank(category.description())) {
                throw new IllegalStateException(
                        "%s: %s 의 %s 에 name 이나 description 이 비어 있습니다.".formatted(path, desiredJob, category.code()));
            }
            if (!codes.add(category.code())) {
                throw new IllegalStateException(
                        "%s: %s 에 같은 code 가 두 번 있습니다: %s".formatted(path, desiredJob, category.code()));
            }
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Catalog(Map<String, List<CompetencyCategory>> jobs) {
        private Catalog {
            jobs = jobs == null ? Map.of() : jobs;
        }
    }
}
