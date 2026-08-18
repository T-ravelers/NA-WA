package me.nawa.member.mapper;

import me.nawa.member.domain.MemberAuthState;
import me.nawa.member.domain.MemberProfile;
import me.nawa.member.dto.MemberAppointmentProfileResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MemberMapper {

    MemberAuthState findAuthState(@Param("memberId") long memberId);

    MemberProfile findProfile(@Param("memberId") long memberId);

    MemberAppointmentProfileResponse findAppointmentProfile(@Param("memberId") long memberId);

    boolean existsActiveCurrency(@Param("currencyCode") String currencyCode);

    int updateProfile(
            @Param("memberId") long memberId,
            @Param("displayName") String displayName,
            @Param("profileImageUrl") String profileImageUrl,
            @Param("nationalityCode") String nationalityCode,
            @Param("preferredLanguage") String preferredLanguage,
            @Param("preferredCurrencyCode") String preferredCurrencyCode
    );

    int completeOnboarding(
            @Param("memberId") long memberId,
            @Param("displayName") String displayName,
            @Param("nationalityCode") String nationalityCode,
            @Param("preferredLanguage") String preferredLanguage,
            @Param("preferredCurrencyCode") String preferredCurrencyCode
    );

    // account_type = 'TRAVELER' 조건이 붙어 있어 재등록은 0행으로 돌아온다.
    int markAsMerchant(
            @Param("memberId") long memberId,
            @Param("businessName") String businessName
    );
}
