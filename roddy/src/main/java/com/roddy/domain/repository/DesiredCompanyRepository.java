package com.roddy.domain.repository;

import com.roddy.domain.DesiredCompany;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DesiredCompanyRepository extends JpaRepository<DesiredCompany, Long> {

    Optional<DesiredCompany> findByUserId(Long userId);
}
