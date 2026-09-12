package com.roddy.domain.analysis.repository;

import com.roddy.domain.analysis.entity.StackDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StackDetailRepository extends JpaRepository<StackDetail, Long> {

    Optional<StackDetail> findByStackName(String stackName);
}
