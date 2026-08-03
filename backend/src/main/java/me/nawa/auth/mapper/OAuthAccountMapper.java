package me.nawa.auth.mapper;

import me.nawa.auth.oauth.account.OAuthLoginAccount;
import me.nawa.auth.oauth.account.OAuthMemberInsert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OAuthAccountMapper {
    OAuthLoginAccount findLoginAccount(
            @Param("provider") String provider,
            @Param("providerUserId") String providerUserId
    );

    int insertMember(OAuthMemberInsert member);

    int insertSocialAccount(
            @Param("memberId") long memberId,
            @Param("provider") String provider,
            @Param("providerUserId") String providerUserId,
            @Param("providerEmail") String providerEmail
    );
}
