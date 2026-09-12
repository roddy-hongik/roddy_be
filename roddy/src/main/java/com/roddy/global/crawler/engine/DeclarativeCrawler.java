package com.roddy.global.crawler.engine;

import com.roddy.global.crawler.CrawlRecord;
import com.roddy.global.crawler.CrawlResult;
import com.roddy.global.crawler.spec.CrawlSpec;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 수집 명세 하나를 실행하는 진입점.
 *
 * <p>회사별 지식은 전부 명세(YAML)에 있고 이 클래스에는 없다. 사이트가 바뀌면 명세만 고치면 된다.
 */
@Slf4j
public class DeclarativeCrawler {

    private final JsonCollector jsonCollector;
    private final EmbeddedJsonCollector embeddedJsonCollector;

    public DeclarativeCrawler(CrawlHttpClient httpClient) {
        this.jsonCollector = new JsonCollector(httpClient);
        this.embeddedJsonCollector = new EmbeddedJsonCollector(httpClient);
    }

    public CrawlResult collect(CrawlSpec spec) {
        if (spec.sourceType() == null) {
            throw new IllegalArgumentException("[%s] source_type 이 없습니다.".formatted(spec.company()));
        }

        List<CrawlRecord> collected = switch (spec.sourceType()) {
            case JSON -> jsonCollector.collect(spec);
            case EMBEDDED_JSON -> embeddedJsonCollector.collect(spec);
            case HTML -> throw new UnsupportedOperationException(
                    "[%s] source_type=html 은 아직 지원하지 않습니다.".formatted(spec.company()));
        };

        List<CrawlRecord> records = RecordFilter.apply(collected, spec.filter());
        if (spec.hasDetail()) {
            log.info("[{}] 상세 본문 수집 설정이 있지만 아직 목록만 수집합니다. 본문은 비어 있을 수 있습니다.",
                    spec.company());
        }

        CrawlResult result = new CrawlResult(spec.company(), records, SpecChecker.check(records, spec));
        if (!result.isHealthy()) {
            log.warn("[{}] 수집 점검 실패 {}건: {}", spec.company(), result.issues().size(), result.issues());
        }
        return result;
    }
}
