package com.roddy.domain.onboarding.service;

import com.roddy.domain.DesiredCompany;
import com.roddy.domain.auth.entity.User;
import com.roddy.domain.auth.repository.UserRepository;
import com.roddy.domain.enums.DesiredJob;
import com.roddy.domain.enums.ExperienceLevel;
import com.roddy.domain.onboarding.dto.request.OnboardingProfileRequest;
import com.roddy.domain.onboarding.dto.response.OnboardingProfileResponse;
import com.roddy.domain.repository.DesiredCompanyRepository;
import com.roddy.global.apiPayload.code.GeneralErrorCode;
import com.roddy.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OnboardingService {

    private final UserRepository userRepository;
    private final DesiredCompanyRepository desiredCompanyRepository;

    @Value("${app.portfolio.upload-dir:uploads/portfolio}")
    private String portfolioUploadDir;

    @Transactional
    public OnboardingProfileResponse completeOnboarding(Long userId, OnboardingProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.USER_NOT_FOUND));

        validatePortfolio(request.getPortfolio());

        StoredPortfolio storedPortfolio = storePortfolio(request.getPortfolio(), userId);
        ExperienceLevel experienceLevel = toExperienceLevel(request.getExperienceYears());
        DesiredJob desiredJob = request.getDesiredJob();

        user.completeProfile(
                request.getName().trim(),
                request.getAge(),
                experienceLevel,
                desiredJob,
                storedPortfolio.path(),
                storedPortfolio.originalFileName(),
                LocalDateTime.now()
        );

        desiredCompanyRepository.findByUserId(userId)
                .ifPresentOrElse(
                        desiredCompany -> desiredCompany.update(desiredJob, request.getDesiredCompany().trim()),
                        () -> desiredCompanyRepository.save(
                                DesiredCompany.create(user, desiredJob, request.getDesiredCompany().trim())
                        )
                );

        return new OnboardingProfileResponse(
                user.getNickname(),
                user.getAge(),
                user.getExperienceYears().name(),
                user.getDesiredJob().name(),
                request.getDesiredCompany().trim(),
                user.getPortfolioFileName(),
                user.isOnboarded(),
                user.isGithubConnected()
        );
    }

    private void validatePortfolio(MultipartFile portfolio) {
        if (portfolio.isEmpty()) {
            throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER, "포트폴리오 파일이 비어 있습니다.");
        }

        String originalFileName = portfolio.getOriginalFilename();
        if (originalFileName == null || !originalFileName.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER, "포트폴리오는 PDF 파일만 업로드할 수 있습니다.");
        }
    }

    private StoredPortfolio storePortfolio(MultipartFile portfolio, Long userId) {
        try {
            Path uploadDirectory = Path.of(portfolioUploadDir).toAbsolutePath().normalize();
            Files.createDirectories(uploadDirectory);

            String originalFileName = portfolio.getOriginalFilename();
            String storedFileName = userId + "-" + UUID.randomUUID() + ".pdf";
            Path storedPath = uploadDirectory.resolve(storedFileName);

            Files.copy(portfolio.getInputStream(), storedPath, StandardCopyOption.REPLACE_EXISTING);

            return new StoredPortfolio(storedPath.toString(), originalFileName);
        } catch (IOException exception) {
            throw new GeneralException(GeneralErrorCode.INTERNAL_SERVER_ERROR, "포트폴리오 파일 저장에 실패했습니다.");
        }
    }

    private ExperienceLevel toExperienceLevel(int experienceYears) {
        if (experienceYears == 0) {
            return ExperienceLevel.NEWBIE;
        }
        if (experienceYears <= 3) {
            return ExperienceLevel.JUNIOR;
        }
        if (experienceYears <= 7) {
            return ExperienceLevel.MIDDLE;
        }
        return ExperienceLevel.SENIOR;
    }

    private record StoredPortfolio(
            String path,
            String originalFileName
    ) {
    }
}
