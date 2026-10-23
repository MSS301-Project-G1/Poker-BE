package com.msspoker.authservice.dto.checkin.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CheckinCalendarResponse {
    private String month;
    private String timezone;
    private LocalDate today;
    private boolean checkedInToday;
    private List<CheckinResponse> days;
}
