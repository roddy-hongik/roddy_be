package com.roddy.domain.mypage.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MyPageProfileUpdateRequest {

    @NotBlank
    private String name;

    @Min(0)
    @Max(100)
    private Integer age;

    /**
     * 새로 올린 프로필 이미지의 객체 키. 프로필 이미지 presign API 로 받은 키를 그대로 보낸다.
     *
     * <p>비워 두면 지금 이미지를 그대로 둔다. 주소는 받지 않는다. 브라우저의 blob: 주소처럼 다른 곳에서
     * 열리지 않는 값이 저장되기 때문이다.
     */
    private String profileImageObjectKey;

    /** true 면 프로필 이미지를 지운다. 새 키와 함께 보낼 수 없다. 보내지 않거나 null 이면 지우지 않는다. */
    private Boolean removeProfileImage;

    public boolean isRemovingProfileImage() {
        return Boolean.TRUE.equals(removeProfileImage);
    }
}
