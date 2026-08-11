package me.nawa.settlement.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SettlementGame {
    private Long settlementId;
    private String gameType;
    private Integer liableCount;
    private String gameStatus;
    private String randomSeed;
}
