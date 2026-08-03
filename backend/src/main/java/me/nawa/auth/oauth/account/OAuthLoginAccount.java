package me.nawa.auth.oauth.account;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class OAuthLoginAccount {
    private long memberId;
    private String memberStatus;
    private boolean memberDeleted;
    private boolean socialAccountDeleted;

    public static OAuthLoginAccount newActive(long memberId) {
        OAuthLoginAccount account = new OAuthLoginAccount();
        account.memberId = memberId;
        account.memberStatus = "ACTIVE";
        return account;
    }
}
