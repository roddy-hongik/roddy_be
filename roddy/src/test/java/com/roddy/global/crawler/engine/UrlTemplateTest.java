package com.roddy.global.crawler.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class UrlTemplateTest {

    @Test
    @DisplayName("레코드 필드 값으로 주소를 조립한다")
    void fillsPlaceholders() {
        String url = UrlTemplate.fill(
                "https://career.woowahan.com/w1/recruits/{recruit_number}",
                Map.of("recruit_number", "R2026001"));

        assertThat(url).isEqualTo("https://career.woowahan.com/w1/recruits/R2026001");
    }

    @Test
    @DisplayName("주소 전체가 필드 값인 경우도 조립한다")
    void fillsWholeHost() {
        String url = UrlTemplate.fill(
                "https://{apply_path}",
                Map.of("apply_path", "recruiter.co.kr/app/jobnotice/view?systemKindCode=MRS2"));

        assertThat(url).isEqualTo("https://recruiter.co.kr/app/jobnotice/view?systemKindCode=MRS2");
    }

    @Test
    @DisplayName("채울 값이 하나라도 없으면 주소를 만들지 않는다")
    void returnsNullWhenValueMissing() {
        Map<String, Object> values = new HashMap<>();
        values.put("job_id", null);

        assertThat(UrlTemplate.fill("https://example.com/jobs/{job_id}", values)).isNull();
        assertThat(UrlTemplate.fill("https://example.com/jobs/{job_id}", Map.of())).isNull();
    }

    @Test
    @DisplayName("자리 표시자가 없으면 템플릿을 그대로 돌려준다")
    void returnsTemplateWithoutPlaceholders() {
        assertThat(UrlTemplate.fill("https://example.com/jobs", Map.of()))
                .isEqualTo("https://example.com/jobs");
        assertThat(UrlTemplate.fill(null, Map.of())).isNull();
    }
}
