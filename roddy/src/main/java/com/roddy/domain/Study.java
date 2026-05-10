package com.roddy.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
@Table(name = "studies")
public class Study extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "study_id")
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(nullable = false)
    private int recruitNum;

    @Column(nullable = false)
    private int currentNum;

    //스터디 개설자
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User organizer;

    public static Study create(String title, String content,
                               int recruitNum, User organizer) {
        return Study.builder()
                .title(title)
                .content(content)
                .recruitNum(recruitNum)
                .currentNum(0)
                .organizer(organizer)
                .build();
    }

    public void increaseCurrentNum() {
        if (this.currentNum >= this.recruitNum) {
            throw new IllegalStateException("스터디 모집 인원이 가득 찼습니다.");
        }
        this.currentNum++;
    }

    public void decreaseCurrentNum() {
        if (this.currentNum <= 0) {
            throw new IllegalStateException("현재 인원은 0명 이하가 될 수 없습니다.");
        }
        this.currentNum--;
    }

    public void update(String title, String content, int recruitNum) {
        this.title = title;
        this.content = content;
        this.recruitNum = recruitNum;
    }
}