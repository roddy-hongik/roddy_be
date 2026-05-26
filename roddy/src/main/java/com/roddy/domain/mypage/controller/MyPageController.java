package com.roddy.domain.mypage.controller;

import com.roddy.domain.mypage.dto.request.MyPageProfileUpdateRequest;
import com.roddy.domain.mypage.dto.response.MyPageProfileResponse;
import com.roddy.domain.mypage.service.MyPageService;
import com.roddy.global.apiPayload.ApiResponse;
import com.roddy.global.apiPayload.code.GeneralErrorCode;
import com.roddy.global.apiPayload.exception.GeneralException;
import com.roddy.global.security.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mypage")
@RequiredArgsConstructor
@Tag(name = "MyPage", description = "마이페이지 API")
public class MyPageController {

    private final MyPageService myPageService;

    @GetMapping("/profile")
    @Operation(summary = "내 프로필 조회")
    public ApiResponse<MyPageProfileResponse> getProfile(
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        return ApiResponse.onSuccess(
                "마이페이지 프로필을 조회했습니다.",
                myPageService.getProfile(requireUser(userDetails))
        );
    }

    @PatchMapping("/profile")
    @Operation(summary = "내 프로필 수정")
    public ApiResponse<MyPageProfileResponse> updateProfile(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody MyPageProfileUpdateRequest request
    ) {
        return ApiResponse.onSuccess(
                "마이페이지 프로필을 수정했습니다.",
                myPageService.updateProfile(requireUser(userDetails), request)
        );
    }

    @DeleteMapping("/me")
    @Operation(summary = "회원 탈퇴")
    public ApiResponse<Void> withdraw(
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        myPageService.withdraw(requireUser(userDetails));
        return ApiResponse.onSuccess("회원 탈퇴가 완료되었습니다.");
    }

    private Long requireUser(UserDetailsImpl userDetails) {
        if (userDetails == null) {
            throw new GeneralException(GeneralErrorCode.MISSING_AUTH_INFO);
        }
        return userDetails.getUser().getId();
    }
}
