package me.nawa.wallet.service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import me.nawa.wallet.domain.enums.TopupMethodType;
import me.nawa.wallet.dto.response.TopupMethodResponse;
import me.nawa.wallet.dto.response.TopupMethodsResponse;
import me.nawa.wallet.mapper.WalletTopupMapper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TopupServiceImpl implements TopupService {
    private static final String GUIDE_MESSAGE = "테스트 환경에서는 실제 결제가 발생하지 않습니다.";

    private final WalletTopupMapper walletTopupMapper;

    @Override
    public TopupMethodsResponse getAvailableTopupMethods() {
        List<TopupMethodResponse> methods = Arrays.stream(TopupMethodType.values())
            .filter(TopupMethodType::isEnabled)
            .map(TopupMethodType::toResponse)
            .collect(Collectors.toList());

        return new TopupMethodsResponse(methods, GUIDE_MESSAGE);
    }
}
