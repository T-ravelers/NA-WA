package me.nawa.notification;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;

/**
 * 미납 리마인더 통합 테스트가 쓰는 정산 픽스처다. 만든 행을 기억해 두었다가 {@link #cleanup()}이
 * 외래키 순서대로 지운다.
 *
 * 리마인더는 DB 전체를 훑으므로 모든 테스트가 DB 하나를 같이 쓰는 환경에서는 남의 행이 대상에
 * 섞일 수 있다. 그래서 정산의 만든 시각을 호출하는 쪽이 정한다. 먼 과거의 연도(테스트 클래스
 * 마다 다른 해)로 만들고 같은 해 근처의 {@code now}로 실행하면, 다른 테스트가 지금 시각으로
 * 만든 행은 "아직 요청한 지 며칠 안 된 정산"이라 대상이 되지 않는다.
 */
public final class ReminderTestData {

    /** 정산 하나와 그 원결제자. 원결제자 본인의 구성원 행(NOT_REQUESTED)도 함께 있다. */
    public record SettlementRow(
        long settlementId,
        long appointmentId,
        long payerMemberId,
        long payerAppointmentMemberId,
        String payerName,
        String gatheringName
    ) {
    }

    /** 정산을 내야 하는 구성원 한 명. */
    public record MemberRow(
        long memberId,
        long appointmentMemberId,
        long settlementMemberId,
        String name
    ) {
    }

    /** 구성원 한 명이 낼 몫. 정산 총액 100 가운데 원결제자 몫 40을 뺀 60이다. */
    public static final BigDecimal SHARE = new BigDecimal("60");

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;
    private final List<Long> memberIds = new ArrayList<>();
    private final List<Long> itemIds = new ArrayList<>();
    private final List<Long> appointmentIds = new ArrayList<>();
    private final List<Long> transferIds = new ArrayList<>();
    private final List<Long> settlementIds = new ArrayList<>();

    public ReminderTestData(DataSource dataSource) {
        this.dataSource = dataSource;
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /** 요청 상태의 정산 하나를 만든다. 만든 시각은 호출하는 쪽이 정한다. */
    public SettlementRow settlement(LocalDateTime createdAt) {
        // members.display_name과 wallet_transfers.transfer_number가 둘 다 VARCHAR(50)이다.
        String unique = UUID.randomUUID().toString();
        String payerName = "rem-p-" + unique;
        String gatheringName = "rem-g-" + unique;
        long payerMemberId = insert("members", "member_id", Map.of("display_name", payerName));
        memberIds.add(payerMemberId);
        long itemId = insert("explore_items", "item_id", Map.of(
            "created_by", payerMemberId,
            "reviewed_by", payerMemberId,
            "item_type", "PLACE",
            "approval_status", "APPROVED",
            "visibility_status", "VISIBLE",
            "reviewed_at", LocalDateTime.now()
        ));
        itemIds.add(itemId);
        long appointmentId = insert("appointments", "appointment_id", Map.of(
            "item_id", itemId,
            "host_member_id", payerMemberId,
            "language_code", "en",
            "appointment_name", gatheringName,
            "max_members", 100,
            "deposit_amount", new BigDecimal("5000"),
            "activity_start_at", LocalDateTime.now().minusHours(2),
            "activity_end_at", LocalDateTime.now().minusHours(1)
        ));
        appointmentIds.add(appointmentId);
        long payerAppointmentMemberId = insert("appointment_members", "appointment_member_id", Map.of(
            "appointment_id", appointmentId,
            "member_id", payerMemberId
        ));
        long transferId = transfer("rem-t-" + unique);
        long settlementId = insert("settlements", "settlement_id", Map.ofEntries(
            Map.entry("appointment_id", appointmentId),
            Map.entry("created_by_member_id", payerMemberId),
            Map.entry("payer_member_id", payerMemberId),
            Map.entry("source_transfer_id", transferId),
            Map.entry("idempotency_key", "rem-" + unique),
            Map.entry("request_fingerprint", "a".repeat(64)),
            Map.entry("settlement_status", "REQUESTED"),
            Map.entry("split_method", "EQUAL"),
            Map.entry("total_amount", new BigDecimal("100")),
            // chk_settlements_receivable이 total = payer_share + receivable까지 확인한다.
            Map.entry("payer_share_amount", new BigDecimal("40")),
            Map.entry("receivable_amount", new BigDecimal("60")),
            Map.entry("created_at", createdAt)
        ));
        settlementIds.add(settlementId);
        insert("settlement_members", "settlement_member_id", Map.of(
            "settlement_id", settlementId,
            "appointment_member_id", payerAppointmentMemberId,
            "share_amount", new BigDecimal("40"),
            "request_status", "NOT_REQUESTED"
        ));
        return new SettlementRow(
            settlementId, appointmentId, payerMemberId, payerAppointmentMemberId,
            payerName, gatheringName
        );
    }

    /** 아직 안 낸(PENDING) 활동 회원 한 명을 정산에 더한다. 리마인더는 아직 한 번도 안 갔다. */
    public MemberRow pendingMember(SettlementRow settlement) {
        return member(settlement, "PENDING");
    }

    /** 아직 안 낸 구성원 {@code count}명을 정산에 더한다. */
    public List<MemberRow> pendingMembers(SettlementRow settlement, int count) {
        List<MemberRow> members = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            members.add(pendingMember(settlement));
        }
        return members;
    }

    /** 이미 낸(PAID) 구성원 한 명을 정산에 더한다. */
    public MemberRow paidMember(SettlementRow settlement) {
        return member(settlement, "PAID");
    }

    private MemberRow member(SettlementRow settlement, String requestStatus) {
        String name = "rem-m-" + UUID.randomUUID();
        long memberId = insert("members", "member_id", Map.of("display_name", name));
        memberIds.add(memberId);
        long appointmentMemberId = insert("appointment_members", "appointment_member_id", Map.of(
            "appointment_id", settlement.appointmentId(),
            "member_id", memberId
        ));
        Map<String, Object> columns = new HashMap<>(Map.of(
            "settlement_id", settlement.settlementId(),
            "appointment_member_id", appointmentMemberId,
            "share_amount", SHARE,
            "request_status", requestStatus
        ));
        if ("PAID".equals(requestStatus)) {
            // chk_settlement_members_paid: 낸 사람은 지급 이체와 지급 시각이 있어야 한다.
            columns.put("paid_transfer_id", transfer("rem-pt-" + UUID.randomUUID()));
            columns.put("paid_at", LocalDateTime.now());
        }
        long settlementMemberId = insert("settlement_members", "settlement_member_id", columns);
        return new MemberRow(memberId, appointmentMemberId, settlementMemberId, name);
    }

    private long transfer(String transferNumber) {
        long transferId = insert("wallet_transfers", "transfer_id", Map.of(
            "currency_code", "KRW",
            "transfer_number", transferNumber,
            "transfer_type", "QR_PAYMENT",
            "transfer_status", "COMPLETED",
            "amount", new BigDecimal("100"),
            "completed_at", LocalDateTime.now()
        ));
        transferIds.add(transferId);
        return transferId;
    }

    /** 리마인더가 이미 몇 번 갔고 마지막이 언제였는지를 정한다. */
    public void setReminder(MemberRow member, int count, LocalDateTime lastRemindedAt) {
        jdbc.update(
            "UPDATE settlement_members SET reminder_count = ?, last_reminded_at = ? "
                + "WHERE settlement_member_id = ?",
            count, lastRemindedAt, member.settlementMemberId()
        );
    }

    public void completeSettlement(SettlementRow settlement) {
        jdbc.update(
            "UPDATE settlements SET settlement_status = 'COMPLETED', completed_at = ? "
                + "WHERE settlement_id = ?",
            LocalDateTime.now(), settlement.settlementId()
        );
    }

    public void softDeleteSettlement(SettlementRow settlement) {
        jdbc.update(
            "UPDATE settlements SET deleted_at = ? WHERE settlement_id = ?",
            LocalDateTime.now(), settlement.settlementId()
        );
    }

    public void softDeleteSettlementMember(MemberRow member) {
        jdbc.update(
            "UPDATE settlement_members SET deleted_at = ? WHERE settlement_member_id = ?",
            LocalDateTime.now(), member.settlementMemberId()
        );
    }

    /** {@code ACTIVE}, {@code SUSPENDED}, {@code WITHDRAWN} 중 하나로 바꾼다. */
    public void setMemberStatus(MemberRow member, String memberStatus) {
        jdbc.update(
            "UPDATE members SET member_status = ? WHERE member_id = ?",
            memberStatus, member.memberId()
        );
    }

    public void softDeleteMember(MemberRow member) {
        jdbc.update(
            "UPDATE members SET deleted_at = ? WHERE member_id = ?",
            LocalDateTime.now(), member.memberId()
        );
    }

    /** 방금 낸 것처럼 구성원을 PAID로 바꾼다. 후보로 뽑힌 뒤에 내는 경쟁을 만들 때 쓴다. */
    public void pay(MemberRow member) {
        jdbc.update(
            "UPDATE settlement_members SET request_status = 'PAID', paid_at = ?, "
                + "paid_transfer_id = ? WHERE settlement_member_id = ?",
            LocalDateTime.now(), transfer("rem-pt-" + UUID.randomUUID()), member.settlementMemberId()
        );
    }

    public int reminderCount(MemberRow member) {
        return jdbc.queryForObject(
            "SELECT reminder_count FROM settlement_members WHERE settlement_member_id = ?",
            Integer.class, member.settlementMemberId()
        );
    }

    public LocalDateTime lastRemindedAt(MemberRow member) {
        return jdbc.queryForObject(
            "SELECT last_reminded_at FROM settlement_members WHERE settlement_member_id = ?",
            LocalDateTime.class, member.settlementMemberId()
        );
    }

    /** 이 회원 앞으로 적힌 리마인더 알림 수다. */
    public int reminderNotificationCount(MemberRow member) {
        return jdbc.queryForObject(
            "SELECT COUNT(*) FROM notifications "
                + "WHERE recipient_member_id = ? AND notification_type = 'SETTLEMENT_REMINDER'",
            Integer.class, member.memberId()
        );
    }

    /** 만든 행을 외래키 순서대로 지운다. 테스트가 실패해도 남지 않게 finally에서 부른다. */
    public void cleanup() {
        delete("notifications", "settlement_id", settlementIds);
        delete("settlement_members", "settlement_id", settlementIds);
        delete("settlements", "settlement_id", settlementIds);
        delete("wallet_transfers", "transfer_id", transferIds);
        delete("appointment_members", "appointment_id", appointmentIds);
        delete("appointments", "appointment_id", appointmentIds);
        delete("explore_items", "item_id", itemIds);
        delete("members", "member_id", memberIds);
    }

    private void delete(String table, String column, List<Long> ids) {
        if (ids.isEmpty()) {
            return;
        }
        String in = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
        jdbc.update("DELETE FROM " + table + " WHERE " + column + " IN (" + in + ")");
    }

    private long insert(String table, String keyColumn, Map<String, ?> values) {
        return new SimpleJdbcInsert(dataSource)
            .withTableName(table)
            .usingColumns(values.keySet().toArray(String[]::new))
            .usingGeneratedKeyColumns(keyColumn)
            .executeAndReturnKey(values)
            .longValue();
    }
}
