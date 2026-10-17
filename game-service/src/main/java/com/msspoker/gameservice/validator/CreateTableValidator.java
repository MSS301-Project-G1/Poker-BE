package com.msspoker.gameservice.validator;

import com.msspoker.gameservice.dto.request.CreateTableRequest;

public interface CreateTableValidator {
    void validate(CreateTableRequest request);
}
