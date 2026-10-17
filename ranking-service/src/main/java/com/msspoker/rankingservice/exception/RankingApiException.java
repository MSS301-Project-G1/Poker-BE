package com.msspoker.rankingservice.exception;

import com.msspoker.rankingservice.enums.RankingError;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class RankingApiException extends RuntimeException {
    private final RankingError code;
    private final HttpStatus status;

    public RankingApiException(RankingError code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }
}
