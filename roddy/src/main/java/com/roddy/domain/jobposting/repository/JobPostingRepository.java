package com.roddy.domain.jobposting.repository;

import com.roddy.domain.enums.JobPostingStatus;
import com.roddy.domain.enums.RecruitType;
import com.roddy.domain.jobposting.entity.JobPosting;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface JobPostingRepository extends JpaRepository<JobPosting, Long> {

    /** 수집 원본의 자연키로 기존 공고를 찾는다. 수집 적재가 upsert 로 동작하는 근거. */
    Optional<JobPosting> findByCompanyCodeAndExternalId(String companyCode, String externalId);

    /** 회사 단위 전량 수집 결과와 비교해 사라진 공고(=마감)를 찾기 위한 조회. */
    List<JobPosting> findAllByCompanyCodeAndStatus(String companyCode, JobPostingStatus status);

    /**
     * 목록 조회. 조건이 비어 있으면 그 조건은 건너뛴다.
     *
     * <p>게시일을 모르는 공고가 섞여 있어 뒤로 밀어낸 뒤 최신순으로 정렬한다.
     */
    @Query(value = """
            select jp from JobPosting jp
            where jp.status = :status
              and (:companyCode is null or jp.companyCode = :companyCode)
              and (:recruitType is null or jp.recruitType = :recruitType)
              and (:keyword is null
                   or lower(jp.title) like :keyword
                   or lower(jp.company) like :keyword)
            order by case when jp.postedAt is null then 1 else 0 end, jp.postedAt desc, jp.id desc
            """,
            countQuery = """
                    select count(jp) from JobPosting jp
                    where jp.status = :status
                      and (:companyCode is null or jp.companyCode = :companyCode)
                      and (:recruitType is null or jp.recruitType = :recruitType)
                      and (:keyword is null
                           or lower(jp.title) like :keyword
                           or lower(jp.company) like :keyword)
                    """)
    Page<JobPosting> search(@Param("status") JobPostingStatus status,
                            @Param("companyCode") String companyCode,
                            @Param("recruitType") RecruitType recruitType,
                            @Param("keyword") String keyword,
                            Pageable pageable);

    /**
     * 조건에 맞는 공고 id 전부. 기본 정렬 순서를 그대로 유지한다.
     *
     * <p>매칭률 순으로 정렬하려면 전체를 견줘 봐야 페이지를 나눌 수 있다. 공고 본문까지 다 읽으면
     * 무겁기 때문에 id 만 가져온다. 조건절이 위 search 와 겹치지만, 엔티티를 읽는 질의와 id 만 읽는
     * 질의를 JPQL 로 합칠 방법이 없다.
     */
    @Query("""
            select jp.id from JobPosting jp
            where jp.status = :status
              and (:companyCode is null or jp.companyCode = :companyCode)
              and (:recruitType is null or jp.recruitType = :recruitType)
              and (:keyword is null
                   or lower(jp.title) like :keyword
                   or lower(jp.company) like :keyword)
            order by case when jp.postedAt is null then 1 else 0 end, jp.postedAt desc, jp.id desc
            """)
    List<Long> searchIds(@Param("status") JobPostingStatus status,
                         @Param("companyCode") String companyCode,
                         @Param("recruitType") RecruitType recruitType,
                         @Param("keyword") String keyword);

    /** 공고 id → 요구 기술 쌍. 매칭률을 계산할 때 공고 본문까지 읽지 않기 위해 따로 둔다. */
    @Query("select jp.id, stack from JobPosting jp join jp.techStacks stack where jp.id in :ids")
    List<Object[]> findTechStacksByIds(@Param("ids") Collection<Long> ids);
}
