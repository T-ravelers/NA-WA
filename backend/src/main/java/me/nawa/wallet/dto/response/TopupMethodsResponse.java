package me.nawa.wallet.dto.response;

import java.util.List;

public record TopupMethodsResponse(
    List<TopupMethodResponse> methods,
    String guideMessage
) {
}
