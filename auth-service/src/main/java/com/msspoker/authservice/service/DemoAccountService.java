package com.msspoker.authservice.service;

import com.msspoker.authservice.dto.auth.response.DemoAccountResponse;

public interface DemoAccountService {
    /** Id of a demo account from auth.data-init; any other email is reported as not found. */
    DemoAccountResponse findDemoAccount(String email);
}
