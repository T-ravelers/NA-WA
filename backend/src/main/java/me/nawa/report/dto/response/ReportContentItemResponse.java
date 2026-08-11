package me.nawa.report.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportContentItemResponse {

    private Long tripItemId;
    private Long itemId;
    private String itemType;
    private String title;
    private String status;
}
