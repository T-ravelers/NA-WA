package me.nawa.member.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 가맹점 등록 요청.
 *
 * 상호명은 members.display_name에 저장한다. QR 결제 조회가 이미 이 값을 payeeName으로
 * 내려주므로 결제자 화면에 상호명이 그대로 표시된다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MerchantRegisterRequest {
    private String businessName;
}
