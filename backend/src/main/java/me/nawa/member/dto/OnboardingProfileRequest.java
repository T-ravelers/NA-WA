package me.nawa.member.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 온보딩 프로필 저장·완료 요청.
 *
 * 네 필드 모두 필수다. 온보딩은 서비스 사용에 필요한 최소 프로필(표시 이름·국적·
 * 선호 언어·선호 통화)을 한 번에 확정하는 절차이므로 부분 저장을 허용하지 않는다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OnboardingProfileRequest {
    private String displayName;
    private String nationalityCode;
    private String preferredLanguage;
    private String preferredCurrencyCode;
}
