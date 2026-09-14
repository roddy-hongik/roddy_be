package com.roddy.domain.community.dto.response;

import java.util.List;

/**
 * 게시글 목록 필터의 선택지. 불러온 페이지와 상관없이 전체 글에서 모은다.
 *
 * @param companies  로드맵 글의 목표 기업과 인터뷰 글의 기업
 * @param jobRoles   로드맵 글의 목표 직무와 인터뷰 글의 직무
 * @param techStacks 로드맵 글의 추천 기술과 인터뷰 글의 기술 스택
 */
public record CommunityFilterOptionsResponse(
        List<String> companies,
        List<String> jobRoles,
        List<String> techStacks
) {
}
