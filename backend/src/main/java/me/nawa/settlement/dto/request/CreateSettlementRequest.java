package me.nawa.settlement.dto.request;

import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CreateSettlementRequest {

    private Long sourceTransferId;
    private String type;
    private List<Long> participantIds;
    private GameCreateRequest game;
    private Long receiptAnalysisId;
}
