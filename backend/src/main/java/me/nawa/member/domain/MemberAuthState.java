package me.nawa.member.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class MemberAuthState {
    private String memberStatus;
    private boolean deleted;
}
