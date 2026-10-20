package com.msspoker.authservice.service;

import com.msspoker.authservice.dto.checkin.response.CheckinCalendarResponse;
import com.msspoker.authservice.dto.checkin.response.CheckinResponse;
import java.util.UUID;

public interface CheckinService {
    CheckinResponse checkIn(UUID accountId);
    CheckinCalendarResponse getCalendar(UUID accountId, String month);
    void retryPendingRewards();
}
