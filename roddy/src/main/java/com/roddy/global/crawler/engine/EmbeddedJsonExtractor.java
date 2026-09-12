package com.roddy.global.crawler.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SSG 페이지에 박혀 있는 JSON 을 꺼낸다.
 *
 * <p>Next.js 는 서버에서 만든 데이터를 {@code <script id="__NEXT_DATA__">} 안에 그대로 넣어두기 때문에,
 * 브라우저를 띄우지 않고도 목록을 받을 수 있다.
 */
public final class EmbeddedJsonExtractor {

    private static final String SCRIPT_PATTERN = "<script[^>]*id=\"%s\"[^>]*>(.*?)</script>";

    private EmbeddedJsonExtractor() {
    }

    /** 해당 script 태그가 없거나 내용이 JSON 이 아니면 null 을 돌려준다. */
    public static JsonNode extract(String html, String scriptId, ObjectMapper objectMapper) {
        if (html == null) {
            return null;
        }

        Pattern pattern = Pattern.compile(
                SCRIPT_PATTERN.formatted(Pattern.quote(scriptId)), Pattern.DOTALL);
        Matcher matcher = pattern.matcher(html);
        if (!matcher.find()) {
            return null;
        }

        try {
            return objectMapper.readTree(matcher.group(1));
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
