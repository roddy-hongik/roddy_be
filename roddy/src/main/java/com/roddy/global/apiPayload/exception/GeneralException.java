package com.roddy.global.apiPayload.exception;

import com.roddy.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;

@Getter
public class GeneralException extends RuntimeException {

    private final BaseErrorCode code;

    public GeneralException(BaseErrorCode code) {
        this.code = code;
    }

    public GeneralException(BaseErrorCode code, String message) {
        super(message);
        this.code = code;
    }
}
