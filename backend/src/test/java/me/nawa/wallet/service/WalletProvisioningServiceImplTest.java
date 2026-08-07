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
    void provisionForMember_walletInsertNotApplied_fails() {
        mapper.walletInsertResult = 0;

        assertThrows(
            IllegalStateException.class,
            () -> service.provisionForMember(77L)
        );
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
        private int walletInsertResult = 1;

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
            provision.setWalletId(generatedWalletId);
            return walletInsertResult;
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
