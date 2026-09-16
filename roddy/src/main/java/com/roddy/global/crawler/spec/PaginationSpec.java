package com.roddy.global.crawler.spec;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.Locale;

/** 목록 페이지네이션 방식. 수집 명세 대부분은 한 번에 전체를 주므로 {@link Type#NONE} 이다. */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PaginationSpec(
        Type type,
        String param,
        Integer start,
        String totalPagesPath
) {

    public PaginationSpec {
        type = type == null ? Type.NONE : type;
    }

    public static PaginationSpec none() {
        return new PaginationSpec(Type.NONE, null, null, null);
    }

    /** 시작값. 페이지 번호는 1부터, offset 은 0부터가 기본이다. */
    public int startValue() {
        if (start != null) {
            return start;
        }
        return type == Type.PAGE_NUMBER ? 1 : 0;
    }

    public enum Type {

        /** 한 번의 요청으로 전체를 받는다. */
        NONE,

        /** 페이지 번호를 1씩 늘린다. */
        PAGE_NUMBER,

        /** 지금까지 받은 건수만큼 offset 을 전진시킨다. */
        OFFSET;

        @JsonCreator
        public static Type from(String value) {
            if (value == null) {
                return NONE;
            }
            return Type.valueOf(value.trim().toUpperCase(Locale.ROOT));
        }
    }
}
