package me.nawa.report.dto.request;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(description = "Report snapshot creation request")
public class ReportCreateRequest {

    @ApiModelProperty(
        value = "Report request locale metadata. In this MVP, snapshot "
            + "titles keep their current source values and are not translated.",
        example = "en"
    )
    private String locale;

    @ApiModelProperty(value = "Selected completed Wallet transfer IDs", example = "[101, 102]")
    private List<Long> transferIds;

    public ReportCreateRequest(String locale) {
        this.locale = locale;
    }
}
