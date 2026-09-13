package com.roddy.domain.roadmap.repository;

import com.roddy.domain.RoadMap;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoadMapRepository extends JpaRepository<RoadMap, Long> {

    Optional<RoadMap> findFirstByUserIdAndFingerprint(Long userId, String fingerprint);

    /** 로드맵 행만 페이지로 읽는다. 기술과 단계는 컬렉션마다 페이지 단위로 묶어 따로 읽는다. */
    Page<RoadMap> findAllByUserIdOrderByIdDesc(Long userId, Pageable pageable);
}
