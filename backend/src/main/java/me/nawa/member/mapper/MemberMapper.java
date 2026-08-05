package me.nawa.member.mapper;

import me.nawa.member.domain.MemberProfile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MemberMapper {

    MemberProfile findProfile(@Param("memberId") long memberId);

    boolean existsActiveCurrency(@Param("currencyCode") String currencyCode);
}
