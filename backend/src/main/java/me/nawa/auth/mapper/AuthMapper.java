package me.nawa.auth.mapper;

import me.nawa.auth.profile.AuthMemberProfile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AuthMapper {
    AuthMemberProfile findMemberProfile(@Param("memberId") long memberId);
}
