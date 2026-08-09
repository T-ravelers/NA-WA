package me.nawa.report.dto.request;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
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
        value = "Report content locale",
        example = "en"
    )
    private String locale;
}
