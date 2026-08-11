package me.nawa.settlement.service;

import lombok.RequiredArgsConstructor;
import me.nawa.settlement.mapper.SettlementMapper;
import org.springframework.stereotype.Service;

/**
 * 정산 서비스 구현체
 *
 * 정산 서비스 계약을 구현하기 위해 정산 영속성 계층을 사용합니다.
 */
@Service
@RequiredArgsConstructor
public class SettlementServiceImpl implements SettlementService {

    private final SettlementMapper settlementMapper;

}
