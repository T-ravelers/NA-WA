package me.nawa.member.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 회원 프로필 부분 수정 요청.
 *
 * 모든 필드는 선택이다. 값이 없으면(필드 부재 또는 null) 그 항목은 변경하지 않는다.
 * 항목을 비우는 사용 사례가 없으므로 부재와 null을 구분하지 않는다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMemberProfileRequest {
    private String displayName;
    private String profileImageUrl;
    private String nationalityCode;
    private String preferredLanguage;
    private String preferredCurrencyCode;
}
