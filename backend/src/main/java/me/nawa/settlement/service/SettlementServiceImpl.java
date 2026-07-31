package me.nawa.settlement.service;

import lombok.RequiredArgsConstructor;
import me.nawa.settlement.mapper.SettlementMapper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SettlementServiceImpl implements SettlementService{

    private final SettlementMapper settlementMapper;

}
