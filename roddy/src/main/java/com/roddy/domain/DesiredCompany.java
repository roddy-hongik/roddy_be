package com.roddy.domain;
import com.roddy.domain.auth.entity.User;
import com.roddy.domain.enums.DesiredJob;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
@Table(name = "desired_company")
public class DesiredCompany {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "desired_company_id")
    private Long id;



    @ManyToOne(fetch = FetchType.LAZY,optional = false)
    @JoinColumn(name = "user_id",nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DesiredJob desiredJob;

    @Column(nullable = false)
    private String desiredCompany;

    public static DesiredCompany create(User user, DesiredJob desiredJob,
                                        String desiredCompany) {
        return DesiredCompany.builder()
                .user(user)
                .desiredJob(desiredJob)
                .desiredCompany(desiredCompany)
                .build();
    }

    public void update(DesiredJob desiredJob, String desiredCompany) {
        this.desiredJob = desiredJob;
        this.desiredCompany = desiredCompany;
    }
}
