package com.msspoker.gameservice.service;

import com.msspoker.gameservice.dto.response.DevTableResponse;

public interface DevTableService {
    DevTableResponse create(int humans, int bots);
}
