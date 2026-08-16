package com.roddy.domain.mypage.repository;

import com.roddy.domain.mypage.entity.DesiredCompany;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DesiredCompanyRepository extends JpaRepository<DesiredCompany, Long> {

    Optional<DesiredCompany> findByUserId(Long userId);
}
