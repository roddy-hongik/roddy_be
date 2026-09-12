package com.roddy.global.crawler.engine;

import com.roddy.global.crawler.CrawlRecord;
import com.roddy.global.crawler.CrawlResult;
import com.roddy.global.crawler.spec.CrawlSpec;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.List;

/**
 * 수집 명세 하나를 실행하는 진입점.
 *
 * <p>회사별 지식은 전부 명세(YAML)에 있고 이 클래스에는 없다. 사이트가 바뀌면 명세만 고치면 된다.
 */
@Slf4j
public class DeclarativeCrawler {

    /** 상세 요청 사이의 기본 간격. 공고 수만큼 요청이 나가므로 회사 사이트에 부담을 주지 않도록 띄운다. */
    public static final Duration DEFAULT_DETAIL_DELAY = Duration.ofMillis(200);

    /** 상세를 전부 받아온다는 뜻. */
    private static final int NO_LIMIT = 0;

    private final JsonCollector jsonCollector;
    private final EmbeddedJsonCollector embeddedJsonCollector;
    private final DetailFetcher detailFetcher;

    public DeclarativeCrawler(CrawlHttpClient httpClient) {
        this(httpClient, DEFAULT_DETAIL_DELAY);
    }

    public DeclarativeCrawler(CrawlHttpClient httpClient, Duration detailDelay) {
        this.jsonCollector = new JsonCollector(httpClient);
        this.embeddedJsonCollector = new EmbeddedJsonCollector(httpClient);
        this.detailFetcher = new DetailFetcher(httpClient, detailDelay);
    }

    public CrawlResult collect(CrawlSpec spec) {
        return collect(spec, NO_LIMIT);
    }

    /**
     * @param detailLimit 상세를 받아올 공고 수. 0 이하면 전부 받는다. 명세가 살아 있는지 몇 건만
     *                    확인해 보고 싶을 때 쓴다.
     */
    public CrawlResult collect(CrawlSpec spec, int detailLimit) {
        if (spec.sourceType() == null) {
            throw new IllegalArgumentException("[%s] source_type 이 없습니다.".formatted(spec.company()));
        }

        List<CrawlRecord> collected = switch (spec.sourceType()) {
            case JSON -> jsonCollector.collect(spec);
            case EMBEDDED_JSON -> embeddedJsonCollector.collect(spec);
            case HTML -> throw new UnsupportedOperationException(
                    "[%s] 목록 수집의 source_type=html 은 아직 지원하지 않습니다.".formatted(spec.company()));
        };

        List<CrawlRecord> records = RecordFilter.apply(collected, spec.filter());
        if (spec.hasDetail()) {
            records = detailFetcher.fetchAll(records, spec, detailLimit);
        }

        CrawlResult result = new CrawlResult(spec.company(), records, SpecChecker.check(records, spec));
        if (!result.isHealthy()) {
            log.warn("[{}] 수집 점검 실패 {}건: {}", spec.company(), result.issues().size(), result.issues());
        }
        if (result.detailFailureCount() > 0) {
            log.warn("[{}] 상세 수집 실패 {}/{}건", spec.company(), result.detailFailureCount(), result.size());
        }
        return result;
    }
}
