package me.nawa.settlement.dto.request;

import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ReceiptAllocationUpdateRequest {

    private List<ReceiptAllocationRequest> allocations;
}
