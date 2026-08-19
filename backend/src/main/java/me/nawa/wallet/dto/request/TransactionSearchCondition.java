package me.nawa.wallet.dto.request;

import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import me.nawa.wallet.domain.enums.TransferStatus;
import me.nawa.wallet.domain.enums.TransferType;
import org.springframework.format.annotation.DateTimeFormat;

@Getter
@Setter
@NoArgsConstructor
public class TransactionSearchCondition {

    private TransferType type;     // 거래 종류 필터 (미지정 시 전체)
    private TransferStatus status; // 거래 상태 필터 (미지정 시 전체)
    // @DateTimeFormat이 없으면 yyyy-MM-dd 문자열이 LocalDate로 바인딩되지 않아 BindException(400)이 된다.
    // EventSearchRequest와 같은 규칙을 쓴다.
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate from;        // 조회 시작일 (포함)

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate to;          // 조회 종료일 (포함)
    private Long cursor;           // 이전 페이지 마지막 항목의 ledgerEntryId
    private Integer size;          // 페이지 크기 (미지정 시 기본값 적용, 최대값으로 캡)
}
