package com.roddy.domain.analysis.service;

import com.roddy.domain.analysis.entity.AnalysisReport;
import com.roddy.domain.auth.entity.User;
import com.roddy.domain.auth.repository.UserRepository;
import com.roddy.domain.enums.DesiredJob;
import com.roddy.domain.enums.JobPostingStatus;
import com.roddy.domain.jobposting.repository.JobPostingRepository;
import com.roddy.domain.mypage.entity.DesiredCompany;
import com.roddy.domain.mypage.repository.DesiredCompanyRepository;
import com.roddy.global.apiPayload.code.GeneralErrorCode;
import com.roddy.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class CompetencyGapReader {

    private static final int MAX_GAP_SKILLS = 10;

    private final UserRepository userRepository;
    private final DesiredCompanyRepository desiredCompanyRepository;
    private final AnalysisReportStore analysisReportStore;
    private final UserTechStackReader userTechStackReader;
    private final JobPostingRepository jobPostingRepository;

    @Transactional(readOnly = true)
    public CompetencyGap read(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.USER_NOT_FOUND));
        AnalysisReport report = analysisReportStore.findLatestCompleted(userId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.ANALYSIS_REPORT_NOT_FOUND));
        DesiredJob targetJob = report.getDesiredJob() != null ? report.getDesiredJob() : user.getDesiredJob();
        if (targetJob == null) {
            throw new GeneralException(GeneralErrorCode.ANALYSIS_REPORT_NOT_FOUND);
        }

        Map<String, Integer> scores = userTechStackReader.read(userId);
        List<String> currentSkills = scores.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey)
                .toList();
        Set<String> currentKeys = currentSkills.stream()
                .map(skill -> skill.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        List<String> gapSkills = jobPostingRepository.countRequiredStacks(JobPostingStatus.OPEN, targetJob).stream()
                .map(row -> (String) row[0])
                .filter(skill -> !currentKeys.contains(skill.toLowerCase(Locale.ROOT)))
                .limit(MAX_GAP_SKILLS)
                .toList();
        String targetCompany = desiredCompanyRepository.findByUserId(userId)
                .map(DesiredCompany::getDesiredCompany)
                .orElse(null);

        return new CompetencyGap(user, targetJob, targetCompany, currentSkills, gapSkills);
    }

    public record CompetencyGap(
            User user,
            DesiredJob targetJob,
            String targetCompany,
            List<String> currentSkills,
            List<String> gapSkills
    ) {
    }
}
