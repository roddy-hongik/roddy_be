package com.roddy.domain.roadmap.controller;

import com.roddy.domain.roadmap.dto.GeneratedRoadMapResponse;
import com.roddy.domain.roadmap.dto.RoadMapSummaryResponse;
import com.roddy.domain.roadmap.dto.SaveRoadMapRequest;
import com.roddy.domain.roadmap.dto.SaveRoadMapResponse;
import com.roddy.domain.roadmap.dto.SavedRoadMapListResponse;
import com.roddy.domain.roadmap.service.RoadMapService;
import com.roddy.global.apiPayload.ApiResponse;
import com.roddy.global.security.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/roadmap")
public class RoadMapController {

    private final RoadMapService roadMapService;

    @GetMapping("/summary")
    @Operation(summary = "로드맵 생성용 역량 요약")
    public ApiResponse<RoadMapSummaryResponse> getSummary(
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        return ApiResponse.onSuccess("로드맵 요약을 조회했습니다.", roadMapService.getSummary(userDetails.getUser().getId()));
    }

    @PostMapping("/generate")
    @Operation(summary = "AI 학습 로드맵 생성")
    public ApiResponse<GeneratedRoadMapResponse> generate(
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        return ApiResponse.onSuccess("로드맵을 생성했습니다.", roadMapService.generate(userDetails.getUser().getId()));
    }

    @GetMapping("/saved")
    @Operation(summary = "저장한 로드맵 목록", description = "최신순. page 는 0부터 시작한다.")
    public ApiResponse<SavedRoadMapListResponse> getSaved(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            Pageable pageable) {
        return ApiResponse.onSuccess("저장한 로드맵을 조회했습니다.",
                roadMapService.getSaved(userDetails.getUser().getId(), pageable));
    }

    @PostMapping("/saved")
    @Operation(summary = "생성한 로드맵 저장")
    public ApiResponse<SaveRoadMapResponse> save(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody SaveRoadMapRequest request) {
        return ApiResponse.onSuccess("로드맵 저장 요청을 처리했습니다.", roadMapService.save(userDetails.getUser().getId(), request));
    }
}
