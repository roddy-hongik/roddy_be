package com.roddy.domain.jobposting.service;

import com.roddy.domain.jobposting.dto.response.CrawlingCompanyResponse;
import com.roddy.domain.jobposting.dto.response.CrawlingDashboardResponse;
import com.roddy.domain.jobposting.entity.CrawlRun;
import com.roddy.domain.jobposting.enums.CrawlingHealth;
import com.roddy.domain.jobposting.repository.CrawlRunRepository;
import com.roddy.global.crawler.spec.CrawlSpec;
import com.roddy.global.crawler.spec.CrawlSpecLoader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 어드민 수집 현황.
 *
 * <p>명세에 있는 회사를 모두 보여준다. 한 번도 돌지 않은 회사가 조용히 빠져 있으면 수집이 멈춘 것을
 * 알아채지 못하기 때문이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminCrawlingService {

    private final CrawlSpecLoader specLoader;
    private final CrawlRunRepository crawlRunRepository;
    private final JobPostingCrawlLauncher crawlLauncher;

    public CrawlingDashboardResponse getDashboard() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();

        Map<String, List<CrawlRun>> todayRuns = groupByCompany(crawlRunRepository.findAllStartedFrom(todayStart));
        Map<String, CrawlRun> latestRuns = latestByCompany(crawlRunRepository.findLatestPerCompany());

        List<CrawlingCompanyResponse> companies = new ArrayList<>();
        for (CrawlSpec spec : specLoader.loadAll()) {
            companies.add(toCompany(spec, todayRuns.getOrDefault(spec.company(), List.of()),
                    latestRuns.get(spec.company())));
        }

        return summarize(companies);
    }

    /**
     * 전체 수집을 뒤에서 시작하고 지금 현황을 돌려준다.
     *
     * <p>수집은 수 분이 걸린다. 끝난 결과는 {@code running} 이 꺼질 때까지 현황을 다시 조회해서 본다.
     */
    public CrawlingDashboardResponse startCrawling() {
        crawlLauncher.startInBackground();
        return getDashboard();
    }

    private CrawlingCompanyResponse toCompany(CrawlSpec spec, List<CrawlRun> today, CrawlRun latest) {
        int collected = today.stream().mapToInt(CrawlRun::getCollectedCount).sum();
        int succeeded = today.stream()
                .mapToInt(run -> run.getCreatedCount() + run.getUpdatedCount() + run.getUnchangedCount())
                .sum();
        int failed = today.stream().mapToInt(CrawlRun::getFailedCount).sum();

        return new CrawlingCompanyResponse(
                spec.company(),
                displayName(spec),
                collected,
                succeeded,
                failed,
                latest == null ? null : latest.getStartedAt(),
                CrawlingHealth.from(latest)
        );
    }

    /** 문제가 있는 회사를 위로 올린다. 22곳을 훑지 않고도 먼저 볼 곳이 보이도록. */
    private CrawlingDashboardResponse summarize(List<CrawlingCompanyResponse> companies) {
        companies.sort(Comparator
                .comparingInt((CrawlingCompanyResponse company) -> switch (company.status()) {
                    case ERROR -> 0;
                    case WARNING -> 1;
                    case HEALTHY -> 2;
                })
                .thenComparing(CrawlingCompanyResponse::id));

        LocalDateTime lastCrawledAt = companies.stream()
                .map(CrawlingCompanyResponse::lastCrawledAt)
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);

        return new CrawlingDashboardResponse(
                companies.stream().mapToInt(CrawlingCompanyResponse::collectedToday).sum(),
                companies.stream().mapToInt(CrawlingCompanyResponse::successCount).sum(),
                companies.stream().mapToInt(CrawlingCompanyResponse::failCount).sum(),
                lastCrawledAt,
                (int) companies.stream().filter(company -> company.status() == CrawlingHealth.ERROR).count(),
                (int) companies.stream().filter(company -> company.status() == CrawlingHealth.WARNING).count(),
                List.copyOf(companies),
                crawlLauncher.isRunning()
        );
    }

    private Map<String, List<CrawlRun>> groupByCompany(List<CrawlRun> runs) {
        Map<String, List<CrawlRun>> grouped = new HashMap<>();
        runs.forEach(run -> grouped.computeIfAbsent(run.getCompanyCode(), key -> new ArrayList<>()).add(run));
        return grouped;
    }

    /** 같은 시각에 두 번 돈 회사가 있어도 첫 건만 취한다. */
    private Map<String, CrawlRun> latestByCompany(List<CrawlRun> runs) {
        Map<String, CrawlRun> latest = new HashMap<>();
        runs.forEach(run -> latest.putIfAbsent(run.getCompanyCode(), run));
        return latest;
    }

    private String displayName(CrawlSpec spec) {
        String nameKo = spec.companyNameKo();
        return (nameKo == null || nameKo.isBlank()) ? spec.company() : nameKo;
    }
}
