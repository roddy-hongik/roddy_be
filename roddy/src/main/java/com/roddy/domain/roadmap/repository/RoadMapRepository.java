package com.roddy.domain.roadmap.repository;

import com.roddy.domain.RoadMap;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoadMapRepository extends JpaRepository<RoadMap, Long> {

    Optional<RoadMap> findFirstByUserIdAndFingerprint(Long userId, String fingerprint);

    List<RoadMap> findAllByUserIdOrderByIdDesc(Long userId);
}
