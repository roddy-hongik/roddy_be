package com.roddy.domain.jobposting.repository;

import com.roddy.domain.jobposting.entity.CrawlRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CrawlRunRepository extends JpaRepository<CrawlRun, Long> {

    /** 회사별 최근 수집 결과. 어드민 수집 현황에서 마지막 상태를 보여줄 때 쓴다. */
    Optional<CrawlRun> findFirstByCompanyCodeOrderByStartedAtDesc(String companyCode);

    List<CrawlRun> findAllByStartedAtAfterOrderByStartedAtDesc(LocalDateTime from);
}
