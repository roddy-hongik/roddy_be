package com.roddy.global.crawler.engine;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 중괄호 자리에 레코드의 필드 값을 끼워 주소를 만든다.
 *
 * <pre>
 * https://careers.kakao.com/jobs/{job_id}
 * https://career.woowahan.com/w1/recruits/{recruit_number}
 * </pre>
 */
public final class UrlTemplate {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z_][A-Za-z0-9_]*)}");

    private UrlTemplate() {
    }

    /**
     * 채울 값이 하나라도 없으면 null 을 돌려준다.
     *
     * <p>파이썬 참조 구현은 값이 없으면 "None" 이라는 글자를 끼워 넣었지만, 그렇게 만든 주소는
     * 어차피 쓸 수 없어 아예 만들지 않는 편이 낫다.
     */
    public static String fill(String template, Map<String, Object> values) {
        if (template == null) {
            return null;
        }

        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder filled = new StringBuilder();
        int cursor = 0;
        while (matcher.find()) {
            Object value = values.get(matcher.group(1));
            if (value == null) {
                return null;
            }
            filled.append(template, cursor, matcher.start()).append(value);
            cursor = matcher.end();
        }
        filled.append(template, cursor, template.length());
        return filled.toString();
    }
}
