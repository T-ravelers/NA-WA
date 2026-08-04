package me.nawa.auth.oauth.account;

import lombok.RequiredArgsConstructor;
import me.nawa.auth.mapper.OAuthAccountMapper;
import me.nawa.auth.oauth.identity.OAuthUserProfile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OAuthMemberTransactionImpl implements OAuthMemberTransaction {
    private static final int MAX_PROVIDER_EMAIL_LENGTH = 320;

    private final OAuthAccountMapper accountMapper;

    @Override
    @Transactional
    public OAuthLoginAccount resolveOrCreate(OAuthUserProfile profile) {
        OAuthUserProfile requiredProfile = Objects.requireNonNull(
                profile,
                "OAuth user profile is required"
        );
        OAuthLoginAccount existing = findAccount(requiredProfile);
        if (existing != null) {
            return existing;
        }

        OAuthMemberInsert member = new OAuthMemberInsert(
                requiredProfile.getDisplayName(),
                requiredProfile.getProfileImageUrl()
        );
        if (accountMapper.insertMember(member) != 1
                || member.getMemberId() <= 0) {
            throw new IllegalStateException("Failed to insert OAuth member");
        }
        if (accountMapper.insertSocialAccount(
                member.getMemberId(),
                requiredProfile.getProvider().getRegistrationId(),
                requiredProfile.getProviderUserId(),
                normalizeProviderEmail(requiredProfile.getEmail())
        ) != 1) {
            throw new IllegalStateException(
                    "Failed to insert OAuth social account"
            );
        }
        return OAuthLoginAccount.newActive(member.getMemberId());
    }

    @Override
    @Transactional(
            readOnly = true,
            propagation = Propagation.REQUIRES_NEW
    )
    public Optional<OAuthLoginAccount> findExisting(
            OAuthUserProfile profile) {
        return Optional.ofNullable(findAccount(Objects.requireNonNull(
                profile,
                "OAuth user profile is required"
        )));
    }

    private OAuthLoginAccount findAccount(OAuthUserProfile profile) {
        return accountMapper.findLoginAccount(
                profile.getProvider().getRegistrationId(),
                profile.getProviderUserId()
        );
    }

    private String normalizeProviderEmail(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= MAX_PROVIDER_EMAIL_LENGTH
                ? normalized
                : null;
    }
}
