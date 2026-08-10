package me.nawa.settlement.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class GameCreateRequest {

    private String type;
    private Integer liableCount;
}
