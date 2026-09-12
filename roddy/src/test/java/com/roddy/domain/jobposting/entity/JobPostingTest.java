package com.roddy.domain.jobposting.entity;

import com.roddy.domain.enums.JobPostingStatus;
import com.roddy.domain.jobposting.dto.JobPostingSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JobPostingTest {

    private static final LocalDateTime CRAWLED_AT = LocalDateTime.of(2026, 3, 16, 9, 0);

    @Test
    @DisplayName("수집 원본이 마감 여부를 주지 않으면 모집중으로 생성한다")
    void createsOpenPostingWhenSourceDoesNotTellClosedState() {
        JobPosting posting = JobPosting.create(snapshot().build(), CRAWLED_AT);

        assertThat(posting.getStatus()).isEqualTo(JobPostingStatus.OPEN);
        assertThat(posting.getClosedAt()).isNull();
        assertThat(posting.getCrawledAt()).isEqualTo(CRAWLED_AT);
        assertThat(posting.getContentHash()).isNotBlank();
    }

    @Test
    @DisplayName("수집 원본이 마감이라고 알려주면 마감 상태로 생성한다")
    void createsClosedPostingWhenSourceSaysClosed() {
        JobPosting posting = JobPosting.create(snapshot().closed(true).build(), CRAWLED_AT);

        assertThat(posting.getStatus()).isEqualTo(JobPostingStatus.CLOSED);
        assertThat(posting.getClosedAt()).isEqualTo(CRAWLED_AT);
    }

    @Test
    @DisplayName("본문이 없는 회사도 적재할 수 있다")
    void allowsPostingWithoutContent() {
        JobPosting posting = JobPosting.create(snapshot().content(null).build(), CRAWLED_AT);

        assertThat(posting.getContent()).isNull();
    }

    @Test
    @DisplayName("재수집 시 변경된 값과 수집 시각을 갱신한다")
    void updatesChangedFields() {
        JobPosting posting = JobPosting.create(snapshot().build(), CRAWLED_AT);
        LocalDateTime recrawledAt = CRAWLED_AT.plusDays(1);

        posting.update(snapshot().title("백엔드 개발자 (신규)").location("제주").build(), recrawledAt);

        assertThat(posting.getTitle()).isEqualTo("백엔드 개발자 (신규)");
        assertThat(posting.getLocation()).isEqualTo("제주");
        assertThat(posting.getCrawledAt()).isEqualTo(recrawledAt);
    }

    @Test
    @DisplayName("재수집한 원본이 마감 여부를 주지 않으면 기존 상태를 유지한다")
    void keepsStatusWhenSourceDoesNotTellClosedState() {
        JobPosting posting = JobPosting.create(snapshot().build(), CRAWLED_AT);
        posting.close(CRAWLED_AT.plusHours(1));

        posting.update(snapshot().build(), CRAWLED_AT.plusDays(1));

        assertThat(posting.getStatus()).isEqualTo(JobPostingStatus.CLOSED);
    }

    @Test
    @DisplayName("재수집한 원본이 모집중이라고 알려주면 마감된 공고를 다시 연다")
    void reopensPostingWhenSourceSaysOpen() {
        JobPosting posting = JobPosting.create(snapshot().closed(true).build(), CRAWLED_AT);

        posting.update(snapshot().closed(false).build(), CRAWLED_AT.plusDays(1));

        assertThat(posting.getStatus()).isEqualTo(JobPostingStatus.OPEN);
        assertThat(posting.getClosedAt()).isNull();
    }

    @Test
    @DisplayName("내용이 그대로면 같은 해시로 판정해 갱신을 건너뛸 수 있다")
    void detectsUnchangedContentByHash() {
        JobPosting posting = JobPosting.create(snapshot().build(), CRAWLED_AT);

        assertThat(posting.hasSameContent(snapshot().build())).isTrue();
        assertThat(posting.hasSameContent(snapshot().title("다른 제목").build())).isFalse();
    }

    @Test
    @DisplayName("수집 시각만 바뀐 경우는 내용 변경으로 보지 않는다")
    void crawledAtDoesNotAffectContentHash() {
        JobPosting posting = JobPosting.create(snapshot().build(), CRAWLED_AT);
        String before = posting.getContentHash();

        posting.touch(CRAWLED_AT.plusDays(3));

        assertThat(posting.getContentHash()).isEqualTo(before);
        assertThat(posting.getCrawledAt()).isEqualTo(CRAWLED_AT.plusDays(3));
    }

    @Test
    @DisplayName("마감일이 지났는지 판별한다")
    void checksDeadlinePassed() {
        JobPosting withDeadline = JobPosting.create(
                snapshot().deadline(LocalDateTime.of(2026, 3, 31, 23, 59)).build(), CRAWLED_AT);
        JobPosting always = JobPosting.create(snapshot().deadline(null).build(), CRAWLED_AT);

        assertThat(withDeadline.isDeadlinePassed(LocalDateTime.of(2026, 4, 1, 0, 0))).isTrue();
        assertThat(withDeadline.isDeadlinePassed(LocalDateTime.of(2026, 3, 20, 0, 0))).isFalse();
        assertThat(always.isDeadlinePassed(LocalDateTime.of(2030, 1, 1, 0, 0))).isFalse();
    }

    @Test
    @DisplayName("수집 원본의 자연키가 없으면 스냅샷을 만들 수 없다")
    void rejectsSnapshotWithoutNaturalKey() {
        assertThatThrownBy(() -> snapshot().externalId(" ").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("externalId");
    }

    @Test
    @DisplayName("마감일이 게시일보다 빠르면 스냅샷을 만들 수 없다")
    void rejectsDeadlineBeforePostedAt() {
        assertThatThrownBy(() -> snapshot()
                .postedAt(LocalDateTime.of(2026, 3, 10, 0, 0))
                .deadline(LocalDateTime.of(2026, 3, 1, 0, 0))
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("마감일");
    }

    private JobPostingSnapshot.JobPostingSnapshotBuilder snapshot() {
        return JobPostingSnapshot.builder()
                .companyCode("kakao")
                .company("카카오")
                .externalId("P-14039")
                .title("백엔드 개발자")
                .content("서버 개발")
                .recruitField("Server")
                .location("판교")
                .applyUrl("https://careers.kakao.com/jobs/P-14039");
    }
}
