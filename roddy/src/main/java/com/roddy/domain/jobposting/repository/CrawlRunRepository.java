package com.roddy.domain.jobposting.repository;

import com.roddy.domain.jobposting.entity.CrawlRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CrawlRunRepository extends JpaRepository<CrawlRun, Long> {

    /** 회사별 최근 수집 결과. 어드민 수집 현황에서 마지막 상태를 보여줄 때 쓴다. */
    Optional<CrawlRun> findFirstByCompanyCodeOrderByStartedAtDesc(String companyCode);

    List<CrawlRun> findAllByStartedAtAfterOrderByStartedAtDesc(LocalDateTime from);

    /**
     * 회사마다 가장 최근 수집 결과 하나씩.
     *
     * <p>회사 수만큼 따로 조회하지 않으려고 한 번에 가져온다. 같은 시각에 두 번 돈 회사가 있으면
     * 둘 다 나올 수 있어, 쓰는 쪽에서 회사별 첫 건만 취한다.
     */
    @Query("""
            select r from CrawlRun r
            where r.startedAt = (
                select max(latest.startedAt) from CrawlRun latest where latest.companyCode = r.companyCode
            )
            order by r.companyCode
            """)
    List<CrawlRun> findLatestPerCompany();

    /** 오늘 돌린 수집 전부. 하루 합계를 낼 때 쓴다. */
    @Query("""
            select r from CrawlRun r
            where r.startedAt >= :from
            order by r.companyCode
            """)
    List<CrawlRun> findAllStartedFrom(@Param("from") LocalDateTime from);
}
