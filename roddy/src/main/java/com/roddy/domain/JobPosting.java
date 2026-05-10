package com.roddy.domain;

import com.roddy.domain.enums.RecruitType;
import com.roddy.domain.enums.JobPostingStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
@Table(name = "job_postings")
public class JobPosting extends BaseEntity{

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "job_id")
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;



    //채용 직무 분야
    @Column(nullable = false)
    private String recruitField;

    @Column(nullable = false)
    private String company;


    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecruitType recruitType; // 인턴/신입/경력직

    private LocalDateTime startDate;
    private LocalDateTime endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobPostingStatus status;

    @PrePersist
    @PreUpdate
    public void validateDates() {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new IllegalStateException("채용 시작일은 종료일보다 이전이어야 합니다.");
        }
    }

    public static JobPosting create(String title, String content, String company,
                                    String recruitField, RecruitType recruitType,
                                    JobPostingStatus status) {
        return JobPosting.builder()
                .title(title)
                .content(content)
                .company(company)
                .recruitField(recruitField)
                .recruitType(recruitType)
                .status(status)
                .build();
    }

    public void update(String title, String content,
                       String company, String recruitField) {
        this.title = title;
        this.content = content;
        this.company = company;
        this.recruitField = recruitField;
    }

    public void updateStatus(JobPostingStatus status) {
        this.status = status;
    }
}