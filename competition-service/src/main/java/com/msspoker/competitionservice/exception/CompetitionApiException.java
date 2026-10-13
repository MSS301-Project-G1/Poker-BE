package com.msspoker.competitionservice.exception;

import com.msspoker.competitionservice.enums.CompetitionError;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class CompetitionApiException extends RuntimeException {
    private final CompetitionError code;
    private final HttpStatus status;

    public CompetitionApiException(CompetitionError code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }
}
