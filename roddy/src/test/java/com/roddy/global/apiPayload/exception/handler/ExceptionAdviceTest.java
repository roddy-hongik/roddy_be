package com.roddy.global.apiPayload.exception.handler;

import static org.assertj.core.api.Assertions.assertThat;

import com.roddy.global.apiPayload.ApiResponse;
import org.apache.tomcat.util.http.InvalidParameterException;
import org.apache.tomcat.util.http.fileupload.impl.FileSizeLimitExceededException;
import org.apache.tomcat.util.http.fileupload.impl.SizeLimitExceededException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

class ExceptionAdviceTest {

    private final ExceptionAdvice advice = new ExceptionAdvice();

    @Test
    void 톰캣이_파일_한_장_용량_초과로_막으면_413_으로_응답한다() {
        var e = new InvalidParameterException(
                new FileSizeLimitExceededException("too big", 11_000_000L, 10_485_760L));

        ResponseEntity<ApiResponse<Object>> response = advice.handleInvalidParameter(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
        assertThat(response.getBody().getCode()).isEqualTo("REQ_4131");
        assertThat(response.getBody().getMessage()).contains("10MB");
    }

    @Test
    void 톰캣이_요청_전체_용량_초과로_막아도_413_으로_응답한다() {
        var e = new InvalidParameterException(new SizeLimitExceededException("too big", 60_000_000L, 52_428_800L));

        assertThat(advice.handleInvalidParameter(e).getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
    }

    @Test
    void 스프링이_먼저_파싱해_용량_초과로_막아도_413_으로_응답한다() {
        var response = advice.handleMaxUploadSizeExceeded(new MaxUploadSizeExceededException(10_485_760L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
    }

    @Test
    void 용량과_관계없는_파라미터_오류는_400_으로_응답한다() {
        var response = advice.handleInvalidParameter(new InvalidParameterException("bad parameter"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getCode()).isEqualTo("REQ_4002");
    }
}
