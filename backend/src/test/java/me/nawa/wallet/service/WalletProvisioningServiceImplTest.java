package me.nawa.wallet.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import me.nawa.wallet.domain.MemberWalletProvision;
import me.nawa.wallet.domain.Wallet;
import me.nawa.wallet.mapper.WalletMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WalletProvisioningServiceImplTest {

    private FakeWalletMapper mapper;
    private WalletProvisioningService service;

    @BeforeEach
    void setUp() {
        mapper = new FakeWalletMapper();
        service = new WalletProvisioningServiceImpl(mapper);
    }

    @Test
    void provisionForMember_createsOwnerThenWalletInKrw() {
        long walletId = service.provisionForMember(77L);

        assertEquals(List.of("owner", "wallet"), mapper.callOrder);
        assertEquals(77L, mapper.provision.getMemberId());
        assertEquals("KRW", mapper.provision.getCurrencyCode());
        assertEquals(mapper.provision.getWalletId(), walletId);
    }

    @Test
    void provisionForMember_ownerInsertProducesNoKey_fails() {
        mapper.generatedWalletOwnerId = 0L;

        assertThrows(
            IllegalStateException.class,
            () -> service.provisionForMember(77L)
        );
        assertEquals(List.of("owner"), mapper.callOrder);
    }

    @Test
    void provisionForMember_walletInsertProducesNoKey_fails() {
        mapper.generatedWalletId = 0L;

        assertThrows(
            IllegalStateException.class,
            () -> service.provisionForMember(77L)
        );
    }

    // soft-delete된 행이 남아 있으면 UNIQUE 제약 때문에 새로 만들 수 없다. 기존 정체성을 되살려야 한다.
    @Test
    void provisionForMember_existingSoftDeletedRows_areRestoredInsteadOfInserted() {
        mapper.existingWalletOwnerId = 41L;
        mapper.existingWalletId = 42L;

        long walletId = service.provisionForMember(77L);

        assertEquals(42L, walletId);
        assertEquals(
            List.of("restoreOwner", "restoreWallet"),
            mapper.callOrder,
            "INSERT 없이 기존 행만 복구돼야 한다"
        );
    }

    @Test
    void provisionForMember_ownerSurvivedButWalletMissing_insertsOnlyWallet() {
        mapper.existingWalletOwnerId = 41L;

        long walletId = service.provisionForMember(77L);

        assertEquals(6L, walletId);
        assertEquals(List.of("restoreOwner", "wallet"), mapper.callOrder);
        assertEquals(41L, mapper.provision.getWalletOwnerId());
    }

    // 복구 UPDATE는 이미 deleted_at IS NULL인 행에 0을 돌려준다. 그것을 실패로 보면 안 된다.
    @Test
    void provisionForMember_restoreAffectsNoRow_stillSucceeds() {
        mapper.existingWalletOwnerId = 41L;
        mapper.existingWalletId = 42L;
        mapper.restoreResult = 0;

        assertEquals(42L, service.provisionForMember(77L));
    }

    @Test
    void provisionForMember_invalidMemberId_isRejectedBeforeAnyInsert() {
        assertThrows(
            IllegalArgumentException.class,
            () -> service.provisionForMember(0L)
        );
        assertEquals(List.of(), mapper.callOrder);
    }

    private static final class FakeWalletMapper implements WalletMapper {
        private final List<String> callOrder = new ArrayList<>();
        private MemberWalletProvision provision;
        private long generatedWalletOwnerId = 5L;
        private long generatedWalletId = 6L;
        private Long existingWalletOwnerId;
        private Long existingWalletId;
        private int restoreResult = 1;

        @Override
        public Wallet findByMemberId(Long memberId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int insertMemberWalletOwner(MemberWalletProvision provision) {
            callOrder.add("owner");
            this.provision = provision;
            provision.setWalletOwnerId(generatedWalletOwnerId);
            return 1;
        }

        @Override
        public int insertMemberWallet(MemberWalletProvision provision) {
            callOrder.add("wallet");
            this.provision = provision;
            provision.setWalletId(generatedWalletId);
            return 1;
        }

        @Override
        public Long findWalletOwnerIdIncludingDeleted(long memberId) {
            return existingWalletOwnerId;
        }

        @Override
        public Long findWalletIdIncludingDeleted(long walletOwnerId) {
            return existingWalletId;
        }

        @Override
        public int restoreWalletOwner(long walletOwnerId) {
            callOrder.add("restoreOwner");
            return restoreResult;
        }

        @Override
        public int restoreWallet(long walletId) {
            callOrder.add("restoreWallet");
            return restoreResult;
        }

        @Override
        public Wallet findByWalletIdForUpdate(Long walletId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateBalance(Long walletId, BigDecimal availableBalance) {
            throw new UnsupportedOperationException();
        }
    }
}
