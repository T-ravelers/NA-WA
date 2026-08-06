package me.nawa.wallet.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.springframework.stereotype.Component;

// wallet_transfers.transfer_number (UNIQUE) 값을 발급한다.
@Component
public class TransactionNumberGenerator {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    public String generate() {
        String datePart = LocalDate.now().format(DATE_FORMAT);
        String randomPart = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return "TXN-" + datePart + "-" + randomPart;
    }
}
