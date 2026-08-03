package me.nawa.auth.oauth.account;

import lombok.RequiredArgsConstructor;
import me.nawa.auth.exception.AuthErrorCode;
import me.nawa.auth.oauth.identity.OAuthUserProfile;
import me.nawa.common.exception.BusinessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class OAuthMemberServiceImpl implements OAuthMemberService {
    private final OAuthMemberTransaction memberTransaction;

    @Override
    public long resolveMemberId(OAuthUserProfile profile) {
        OAuthLoginAccount account;
        try {
            account = memberTransaction.resolveOrCreate(profile);
        } catch (DuplicateKeyException exception) {
            account = memberTransaction.findExisting(profile)
                    .orElseThrow(
                            () -> new BusinessException(
                                    AuthErrorCode
                                            .OAUTH_ACCOUNT_PROVISION_FAILED
                            )
                    );
        }
        return requireLoginAllowed(account);
    }

    private long requireLoginAllowed(OAuthLoginAccount account) {
        if (account == null || account.getMemberId() <= 0) {
            throw new BusinessException(
                    AuthErrorCode.OAUTH_ACCOUNT_PROVISION_FAILED
            );
        }
        if (account.isMemberDeleted()
                || account.isSocialAccountDeleted()) {
            throw new BusinessException(
                    AuthErrorCode.OAUTH_MEMBER_WITHDRAWN
            );
        }

        String status = account.getMemberStatus();
        if (status == null) {
            throw new BusinessException(
                    AuthErrorCode.OAUTH_ACCOUNT_PROVISION_FAILED
            );
        }
        return switch (status.toUpperCase(Locale.ROOT)) {
            case "ACTIVE" -> account.getMemberId();
            case "SUSPENDED" -> throw new BusinessException(
                    AuthErrorCode.OAUTH_MEMBER_SUSPENDED
            );
            case "WITHDRAWN" -> throw new BusinessException(
                    AuthErrorCode.OAUTH_MEMBER_WITHDRAWN
            );
            default -> throw new BusinessException(
                    AuthErrorCode.OAUTH_ACCOUNT_PROVISION_FAILED
            );
        };
    }
}
