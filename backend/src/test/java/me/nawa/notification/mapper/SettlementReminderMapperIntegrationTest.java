package me.nawa.notification.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.List;
import me.nawa.config.MySqlSchemaExtension;
import me.nawa.notification.ReminderTestData;
import me.nawa.notification.ReminderTestData.MemberRow;
import me.nawa.notification.ReminderTestData.SettlementRow;
import me.nawa.notification.domain.SettlementReminderCriteria;
import me.nawa.notification.domain.SettlementReminderTarget;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;

/**
 * 리마인더 대상을 고르고 잠그는 SQL의 조건을 고정한다.
 *
 * 리마인더는 DB 전체를 훑고 모든 테스트가 DB 하나를 같이 쓰므로, 이 클래스의 정산은 2001년에
 * 만들고 {@code now}도 같은 해로 넘긴다. 다른 테스트가 지금 시각으로 만든 정산은 "요청한 지
 * 얼마 안 된 정산"이라 여기서 대상이 되지 않는다.
 *
 * 며칠째에 보내는지(3일·3일·3회)는 서비스가 계산해 criteria로 넘기는 정책이라 서비스 테스트가
 * 맡는다. 여기서는 받은 기준을 SQL이 정확히 비교하는지만 본다.
 */
@ExtendWith(MySqlSchemaExtension.class)
@EnabledIfEnvironmentVariable(
    named = "RUN_MYSQL_INTEGRATION_TESTS",
    matches = "(?i)true"
)
class SettlementReminderMapperIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2001, 6, 15, 10, 0);
    // 기준 시각은 둘 다 NOW의 이틀 전 자정이다. 경계 비교를 한 값으로 확인할 수 있게 같게 둔다.
    private static final LocalDateTime BEFORE = LocalDateTime.of(2001, 6, 13, 0, 0);
    private static final int MAX_COUNT = 3;
    private static final SettlementReminderCriteria CRITERIA =
        new SettlementReminderCriteria(NOW, BEFORE, BEFORE, MAX_COUNT);
    // BEFORE보다 앞서 만든(= 기준을 채운) 정산의 만든 시각.
    private static final LocalDateTime DUE_CREATED_AT = BEFORE.minusDays(1);

    private static HikariDataSource dataSource;
    private static SettlementReminderMapper mapper;

    private ReminderTestData data;

    @BeforeAll
    static void setUpDatabase() throws Exception {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setDriverClassName(requiredEnvironment("DATABASE_DRIVER"));
        hikariConfig.setJdbcUrl(requiredEnvironment("DATABASE_URL"));
        hikariConfig.setUsername(requiredEnvironment("DATABASE_USERNAME"));
        hikariConfig.setPassword(requiredEnvironment("DATABASE_PASSWORD"));
        hikariConfig.setMaximumPoolSize(3);
        hikariConfig.setMinimumIdle(0);
        dataSource = new HikariDataSource(hikariConfig);

        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setConfigLocation(new ClassPathResource("mybatis-config.xml"));
        factoryBean.setMapperLocations(
            new ClassPathResource("me/nawa/notification/mapper/SettlementReminderMapper.xml")
        );
        SqlSessionFactory sqlSessionFactory = factoryBean.getObject();
        if (!sqlSessionFactory.getConfiguration().hasMapper(SettlementReminderMapper.class)) {
            sqlSessionFactory.getConfiguration().addMapper(SettlementReminderMapper.class);
        }
        mapper = new SqlSessionTemplate(sqlSessionFactory).getMapper(SettlementReminderMapper.class);
    }

    @AfterAll
    static void closeDatabase() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @BeforeEach
    void setUp() {
        data = new ReminderTestData(dataSource);
    }

    @AfterEach
    void tearDown() {
        data.cleanup();
    }

    /**
     * 번호 오름차순, 커서(afterId)는 "그 번호보다 큰 것부터", limit은 실제로 자른다.
     *
     * 다른 테스트의 행이 섞이지 않는 해(2001)에서만 만든 행이므로 결과 전체를 그대로 견준다.
     */
    @Test
    void findCandidateIds_returnsDueMembersAscendingAndHonorsCursorAndLimit() {
        SettlementRow settlement = data.settlement(DUE_CREATED_AT);
        MemberRow first = data.pendingMember(settlement);
        MemberRow second = data.pendingMember(settlement);
        MemberRow third = data.pendingMember(settlement);

        assertEquals(
            List.of(first.settlementMemberId(), second.settlementMemberId(), third.settlementMemberId()),
            mapper.findCandidateIds(CRITERIA, 0, 100)
        );
        assertEquals(
            List.of(first.settlementMemberId(), second.settlementMemberId()),
            mapper.findCandidateIds(CRITERIA, 0, 2)
        );
        assertEquals(
            List.of(third.settlementMemberId()),
            mapper.findCandidateIds(CRITERIA, second.settlementMemberId(), 100)
        );
        assertEquals(List.of(), mapper.findCandidateIds(CRITERIA, third.settlementMemberId(), 100));
    }

    /**
     * 첫 리마인더는 정산을 만든 시각이 기준보다 **엄격히 앞서야** 한다. 같은 시각은 아직 아니다.
     * 이 경계가 한 칸 어긋나면 하루 일찍, 또는 하루 늦게 첫 알림이 나간다.
     */
    @Test
    void findCandidateIds_firstReminder_needsSettlementCreatedStrictlyBeforeTheLimit() {
        MemberRow justBefore = data.pendingMember(data.settlement(BEFORE.minusSeconds(1)));
        MemberRow exactlyAtLimit = data.pendingMember(data.settlement(BEFORE));
        MemberRow afterLimit = data.pendingMember(data.settlement(BEFORE.plusSeconds(1)));

        List<Long> found = mapper.findCandidateIds(CRITERIA, 0, 100);

        assertEquals(List.of(justBefore.settlementMemberId()), found);
        assertTrue(!found.contains(exactlyAtLimit.settlementMemberId()));
        assertTrue(!found.contains(afterLimit.settlementMemberId()));
    }

    /**
     * 다시 보내는 것은 마지막 리마인더가 기준보다 **엄격히 앞설 때**뿐이다. 아직 한 번도 안
     * 보냈다면(NULL) 조건 없이 대상이다.
     */
    @Test
    void findCandidateIds_repeat_needsLastRemindedStrictlyBeforeTheLimit() {
        SettlementRow settlement = data.settlement(DUE_CREATED_AT);
        MemberRow never = data.pendingMember(settlement);
        MemberRow longAgo = data.pendingMember(settlement);
        data.setReminder(longAgo, 1, BEFORE.minusSeconds(1));
        MemberRow exactlyAtLimit = data.pendingMember(settlement);
        data.setReminder(exactlyAtLimit, 1, BEFORE);
        MemberRow recently = data.pendingMember(settlement);
        data.setReminder(recently, 1, NOW);

        assertEquals(
            List.of(never.settlementMemberId(), longAgo.settlementMemberId()),
            mapper.findCandidateIds(CRITERIA, 0, 100)
        );
    }

    /** 횟수 상한에 닿은 사람에게는 간격이 아무리 지나도 더 보내지 않는다. */
    @Test
    void findCandidateIds_stopsOnceTheCountReachesTheCap() {
        SettlementRow settlement = data.settlement(DUE_CREATED_AT);
        MemberRow oneLeft = data.pendingMember(settlement);
        data.setReminder(oneLeft, MAX_COUNT - 1, BEFORE.minusDays(30));
        MemberRow capped = data.pendingMember(settlement);
        data.setReminder(capped, MAX_COUNT, BEFORE.minusDays(30));

        assertEquals(List.of(oneLeft.settlementMemberId()), mapper.findCandidateIds(CRITERIA, 0, 100));
    }

    /**
     * 보내면 안 되는 사람은 한 명도 후보로 뽑히지 않는다. 대상 하나(due)만 남는다.
     *
     * 원결제자 본인 행은 settlement() 안에 이미 있다(NOT_REQUESTED).
     */
    @Test
    void findCandidateIds_skipsPaidCompletedDeletedAndInactiveMembers() {
        SettlementRow settlement = data.settlement(DUE_CREATED_AT);
        MemberRow due = data.pendingMember(settlement);
        data.paidMember(settlement);

        MemberRow withdrawn = data.pendingMember(settlement);
        data.setMemberStatus(withdrawn, "WITHDRAWN");
        MemberRow suspended = data.pendingMember(settlement);
        data.setMemberStatus(suspended, "SUSPENDED");
        MemberRow deletedMember = data.pendingMember(settlement);
        data.softDeleteMember(deletedMember);
        MemberRow deletedRow = data.pendingMember(settlement);
        data.softDeleteSettlementMember(deletedRow);

        SettlementRow completed = data.settlement(DUE_CREATED_AT);
        data.pendingMember(completed);
        data.completeSettlement(completed);
        SettlementRow deletedSettlement = data.settlement(DUE_CREATED_AT);
        data.pendingMember(deletedSettlement);
        data.softDeleteSettlement(deletedSettlement);

        assertEquals(List.of(due.settlementMemberId()), mapper.findCandidateIds(CRITERIA, 0, 100));
    }

    /**
     * 잠그는 쪽이 알림에 복사할 값을 함께 읽는다. 받는 사람은 구성원 본인이고, 이름은 돈을 받을
     * 원결제자이며, 금액은 그 사람 몫이고, 통화는 원거래에서 온다.
     */
    @Test
    void lockTargets_readsTheValuesTheNotificationCopies() {
        SettlementRow settlement = data.settlement(DUE_CREATED_AT);
        MemberRow member = data.pendingMember(settlement);

        List<SettlementReminderTarget> targets =
            mapper.lockTargets(CRITERIA, List.of(member.settlementMemberId()));

        assertEquals(1, targets.size());
        SettlementReminderTarget target = targets.get(0);
        assertEquals(member.settlementMemberId(), target.getSettlementMemberId());
        assertEquals(member.memberId(), target.getRecipientMemberId());
        assertEquals(settlement.settlementId(), target.getSettlementId());
        assertEquals(settlement.payerName(), target.getPayerName());
        assertEquals(settlement.gatheringName(), target.getGatheringName());
        assertEquals(0, ReminderTestData.SHARE.compareTo(target.getShareAmount()));
        assertEquals("KRW", target.getCurrencyCode());
    }

    /**
     * 후보로 뽑힌 뒤에 낸 사람은 잠글 때 같은 조건으로 걸러진다. 이 재확인이 없으면 방금 낸
     * 사람에게 "아직 안 냈다"는 알림이 나간다.
     */
    @Test
    void lockTargets_dropsAMemberWhoPaidAfterBeingPickedAsCandidate() {
        SettlementRow settlement = data.settlement(DUE_CREATED_AT);
        MemberRow stillDue = data.pendingMember(settlement);
        MemberRow paidMeanwhile = data.pendingMember(settlement);
        List<Long> candidates = mapper.findCandidateIds(CRITERIA, 0, 100);
        assertEquals(2, candidates.size());

        data.pay(paidMeanwhile);

        List<SettlementReminderTarget> targets = mapper.lockTargets(CRITERIA, candidates);
        assertEquals(
            List.of(stillDue.settlementMemberId()),
            targets.stream().map(SettlementReminderTarget::getSettlementMemberId).toList()
        );
    }

    /**
     * 다른 트랜잭션이 잡은 구성원은 기다리지 않고 건너뛴다. 다른 인스턴스가 처리 중이거나 그
     * 사람이 지금 내는 중이라는 뜻이다. 기다리면 인스턴스 둘이 같은 묶음 앞에서 줄을 선다.
     */
    @Test
    void lockTargets_skipsAMemberLockedByAnotherTransaction() throws Exception {
        SettlementRow settlement = data.settlement(DUE_CREATED_AT);
        MemberRow locked = data.pendingMember(settlement);
        MemberRow free = data.pendingMember(settlement);

        try (Connection holder = dataSource.getConnection()) {
            holder.setAutoCommit(false);
            try (PreparedStatement lock = holder.prepareStatement(
                "SELECT settlement_member_id FROM settlement_members "
                    + "WHERE settlement_member_id = ? FOR UPDATE")) {
                lock.setLong(1, locked.settlementMemberId());
                lock.executeQuery().close();

                List<SettlementReminderTarget> targets = mapper.lockTargets(
                    CRITERIA, List.of(locked.settlementMemberId(), free.settlementMemberId())
                );

                assertEquals(
                    List.of(free.settlementMemberId()),
                    targets.stream().map(SettlementReminderTarget::getSettlementMemberId).toList()
                );
            } finally {
                holder.rollback();
            }
        }
    }

    /**
     * 정산 행을 지급이 쥐고 있으면 그 정산의 구성원도 건너뛴다.
     *
     * 알림을 적을 때 외래키 검사가 정산 행에 공유 잠금을 잡는데, 지급은 정산 행을 먼저 잡고
     * 구성원 행을 잡는다. 두 잠금을 같은 쿼리에서 함께 잡아 두지 않으면 같은 사람의 지급과
     * 리마인더가 서로를 기다린다. 정산 행에 공유 잠금을 걸지 않으면 이 건너뜀이 사라진다.
     */
    @Test
    void lockTargets_skipsMembersOfASettlementLockedByAPayment() throws Exception {
        SettlementRow beingPaid = data.settlement(DUE_CREATED_AT);
        MemberRow inBeingPaid = data.pendingMember(beingPaid);
        SettlementRow other = data.settlement(DUE_CREATED_AT);
        MemberRow inOther = data.pendingMember(other);

        try (Connection payment = dataSource.getConnection()) {
            payment.setAutoCommit(false);
            try (PreparedStatement lock = payment.prepareStatement(
                "SELECT settlement_id FROM settlements WHERE settlement_id = ? FOR UPDATE")) {
                lock.setLong(1, beingPaid.settlementId());
                lock.executeQuery().close();

                List<SettlementReminderTarget> targets = mapper.lockTargets(
                    CRITERIA, List.of(inBeingPaid.settlementMemberId(), inOther.settlementMemberId())
                );

                assertEquals(
                    List.of(inOther.settlementMemberId()),
                    targets.stream().map(SettlementReminderTarget::getSettlementMemberId).toList()
                );
            } finally {
                payment.rollback();
            }
        }
    }

    /**
     * 발송 기록은 횟수를 하나 올리고 앱이 넘긴 시각을 그대로 적는다. 먼 과거의 고정 값을 쓰므로
     * DB 시계로 적었다면 우연히도 같을 수 없다.
     */
    @Test
    void markReminded_countsUpAndStoresTheTimeGivenByApplication() {
        SettlementRow settlement = data.settlement(DUE_CREATED_AT);
        MemberRow member = data.pendingMember(settlement);
        MemberRow untouched = data.pendingMember(settlement);

        assertEquals(1, mapper.markReminded(List.of(member.settlementMemberId()), NOW));
        assertEquals(1, data.reminderCount(member));
        assertEquals(NOW, data.lastRemindedAt(member));

        LocalDateTime later = NOW.plusDays(3);
        assertEquals(1, mapper.markReminded(List.of(member.settlementMemberId()), later));
        assertEquals(2, data.reminderCount(member));
        assertEquals(later, data.lastRemindedAt(member));

        // 넘기지 않은 구성원은 그대로다.
        assertEquals(0, data.reminderCount(untouched));
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required");
        }
        return value;
    }
}
