package com.roddy.global.crawler.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;

/**
 * 채용 사이트를 호출하는 HTTP 클라이언트.
 *
 * <p>응답을 문자열로만 받는다. JSON 파싱은 호출하는 쪽에서 하고, HTML(임베드 JSON) 도 같은 경로를 탄다.
 * 그리팅처럼 커스텀 도메인에서 301 로 넘기는 사이트가 있어 리다이렉트를 따라간다.
 */
public class CrawlHttpClient {

    private static final String USER_AGENT = "Mozilla/5.0 (compatible; roddy-collector/0.1)";
    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CrawlHttpClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public static CrawlHttpClient create() {
        HttpClient httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(TIMEOUT)
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(TIMEOUT);

        return new CrawlHttpClient(RestClient.builder()
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
                .build());
    }

    public String fetch(String url, String method, Map<String, Object> params,
                        Map<String, String> headers, Map<String, Object> body) {
        RestClient.RequestBodySpec request = restClient
                .method(HttpMethod.valueOf(method))
                .uri(buildUri(url, params));

        headers.forEach(request::header);
        if (body != null) {
            if (!hasContentType(headers)) {
                request.contentType(MediaType.APPLICATION_JSON);
            }
            request.body(toJson(body));
        }

        return decode(request.retrieve().toEntity(byte[].class));
    }

    /**
     * 응답 본문을 문자열로 바꾼다.
     *
     * <p>Content-Type 에 charset 이 없으면 UTF-8 로 읽는다. 스프링 기본값인 ISO-8859-1 로 읽으면
     * charset 을 빼고 내려주는 사이트에서 한글이 깨진다.
     */
    private String decode(ResponseEntity<byte[]> response) {
        byte[] body = response.getBody();
        if (body == null) {
            return "";
        }

        MediaType contentType = response.getHeaders().getContentType();
        Charset charset = contentType != null && contentType.getCharset() != null
                ? contentType.getCharset()
                : StandardCharsets.UTF_8;
        return new String(body, charset);
    }

    private URI buildUri(String url, Map<String, Object> params) {
        if (params.isEmpty()) {
            return URI.create(url);
        }

        MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>();
        params.forEach((key, value) -> queryParams.add(key, String.valueOf(value)));

        return UriComponentsBuilder.fromUriString(url)
                .queryParams(queryParams)
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUri();
    }

    private boolean hasContentType(Map<String, String> headers) {
        return headers.keySet().stream()
                .anyMatch(name -> name.toLowerCase(Locale.ROOT).equals(HttpHeaders.CONTENT_TYPE.toLowerCase(Locale.ROOT)));
    }

    private String toJson(Map<String, Object> body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("수집 명세의 요청 본문을 JSON 으로 만들지 못했습니다.", e);
        }
    }
}
