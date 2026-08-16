package com.roddy.global.apiPayload.code;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum GeneralErrorCode implements BaseErrorCode {

    // 인증 에러
    DUPLICATE_LOGINID(HttpStatus.BAD_REQUEST, "AUTH_4001", "중복되는 아이디가 존재합니다."),
    INVALID_AUTH_CODE(HttpStatus.BAD_REQUEST, "AUTH_4002", "이메일 인증번호가 유효하지 않습니다."),
    EMAIL_NOT_VERIFIED(HttpStatus.BAD_REQUEST, "AUTH_4003", "이메일 인증이 필요합니다."),
    SOCIAL_LOGIN_REQUIRED(HttpStatus.BAD_REQUEST, "AUTH_4004", "소셜 로그인을 이용해주세요."),
    MISSING_AUTH_INFO(HttpStatus.UNAUTHORIZED, "AUTH_4011", "인증 정보가 누락되었습니다."),
    INVALID_LOGIN(HttpStatus.UNAUTHORIZED, "AUTH_4012", "올바르지 않은 아이디, 혹은 비밀번호입니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_4013", "유효하지 않은 토큰입니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "AUTH_4031", "접근 권한이 없습니다."),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "AUTH_4191", "토큰이 만료되었습니다."),

    // 서버 내부 에러
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "SERVER_5001", "서버 내부 오류입니다."),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "SERVER_5031", "서버가 일시적으로 불안정합니다."),
    EXTERNAL_SERVICE_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "SERVER_5041", "외부 서비스 응답 지연"),

    // 요청 파라미터 에러
    MISSING_PARAMETER(HttpStatus.BAD_REQUEST, "REQ_4001", "필수 파라미터가 누락되었습니다."),
    INVALID_PARAMETER(HttpStatus.BAD_REQUEST, "REQ_4002", "파라미터 형식이 잘못되었습니다."),
    UNSUPPORTED_CONTENT_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "REQ_4151", "지원하지 않는 Content-Type입니다."),

    // 유저 에러
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_4041", "유저를 찾을 수 없습니다."),

    // 커뮤니티 에러
    COMMUNITY_POST_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMUNITY_4041", "게시글을 찾을 수 없습니다."),
    COMMUNITY_POST_ALREADY_REPORTED(HttpStatus.CONFLICT, "COMMUNITY_4091", "이미 신고한 게시글입니다."),
    COMMUNITY_COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMUNITY_4042", "댓글을 찾을 수 없습니다."),
    COMMUNITY_COMMENT_PARENT_INVALID(HttpStatus.BAD_REQUEST, "COMMUNITY_4001", "대댓글은 최상위 댓글에만 작성할 수 있습니다."),
    COMMUNITY_COMMENT_ALREADY_REPORTED(HttpStatus.CONFLICT, "COMMUNITY_4092", "이미 신고한 댓글입니다."),
    COMMUNITY_COMMENT_DELETE_FORBIDDEN(HttpStatus.FORBIDDEN, "COMMUNITY_4031", "본인 댓글만 삭제할 수 있습니다."),

    // 스터디 에러
    STUDY_NOT_FOUND(HttpStatus.NOT_FOUND, "STUDY_4041", "스터디 모집글을 찾을 수 없습니다."),
    STUDY_APPLICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "STUDY_4042", "스터디 지원 내역을 찾을 수 없습니다."),
    STUDY_FORBIDDEN(HttpStatus.FORBIDDEN, "STUDY_4031", "해당 스터디 모집글에 대한 권한이 없습니다."),
    STUDY_ALREADY_CLOSED(HttpStatus.CONFLICT, "STUDY_4091", "이미 모집 완료된 스터디입니다."),
    STUDY_CAPACITY_FULL(HttpStatus.CONFLICT, "STUDY_4092", "스터디 모집 인원이 가득 찼습니다."),
    STUDY_ALREADY_APPLIED(HttpStatus.CONFLICT, "STUDY_4093", "이미 지원한 스터디입니다."),
    STUDY_APPLICATION_ALREADY_CANCELED(HttpStatus.CONFLICT, "STUDY_4094", "이미 취소된 스터디 지원입니다."),
    STUDY_APPLICATION_STATUS_ALREADY_PROCESSED(HttpStatus.CONFLICT, "STUDY_4095", "이미 처리된 스터디 지원 상태입니다."),
    STUDY_REOPEN_NOT_AVAILABLE(HttpStatus.CONFLICT, "STUDY_4096", "모집 인원이 모두 확정된 스터디는 다시 모집중으로 변경할 수 없습니다."),
    STUDY_AUTHOR_CANNOT_APPLY(HttpStatus.BAD_REQUEST, "STUDY_4001", "작성자는 자신의 스터디에 지원할 수 없습니다."),
    INVALID_STUDY_MODE_LOCATION(HttpStatus.BAD_REQUEST, "STUDY_4002", "대면 스터디는 장소가 필수입니다."),
    INVALID_STUDY_SCHEDULE(HttpStatus.BAD_REQUEST, "STUDY_4003", "스터디 진행 시간은 현재 시각 이후여야 합니다."),
    INVALID_STUDY_APPLICATION_STATUS(HttpStatus.BAD_REQUEST, "STUDY_4004", "스터디 지원 상태는 ACCEPTED 또는 REJECTED만 설정할 수 있습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
