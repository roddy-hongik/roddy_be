package com.roddy.domain.jobposting.repository;

import com.roddy.domain.enums.JobPostingStatus;
import com.roddy.domain.jobposting.entity.JobPosting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JobPostingRepository extends JpaRepository<JobPosting, Long> {

    /** 수집 원본의 자연키로 기존 공고를 찾는다. 수집 적재가 upsert 로 동작하는 근거. */
    Optional<JobPosting> findByCompanyCodeAndExternalId(String companyCode, String externalId);

    /** 회사 단위 전량 수집 결과와 비교해 사라진 공고(=마감)를 찾기 위한 조회. */
    List<JobPosting> findAllByCompanyCodeAndStatus(String companyCode, JobPostingStatus status);
}
