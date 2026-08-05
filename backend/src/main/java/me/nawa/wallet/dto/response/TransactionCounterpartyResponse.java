package me.nawa.wallet.dto.response;

public record TransactionCounterpartyResponse(
    String type, // 상대방 종류 (MEMBER | SYSTEM | EXTERNAL)
    String name  // 상대방 표시 이름
) {
}
