package me.nawa.settlement.service;

import lombok.RequiredArgsConstructor;
import me.nawa.common.exception.BusinessException;
import me.nawa.settlement.domain.Settlement;
import me.nawa.settlement.domain.SettlementMember;
import me.nawa.settlement.domain.SettlementSource;
import me.nawa.settlement.domain.SettlementSummary;
import me.nawa.settlement.domain.SettlementParticipant;
import me.nawa.settlement.domain.SettlementDetail;
import me.nawa.settlement.domain.ReceiptAnalysis;
import me.nawa.settlement.domain.ReceiptAnalysisItem;
import me.nawa.settlement.domain.ReceiptItemAllocation;
import me.nawa.settlement.domain.ReceiptAllocationView;
import me.nawa.settlement.domain.SettlementGame;
import me.nawa.settlement.domain.SettlementGameMember;
import me.nawa.settlement.dto.request.CreateSettlementRequest;
import me.nawa.settlement.dto.response.SettlementCreateResponse;
import me.nawa.settlement.dto.response.SettlementListResponse;
import me.nawa.settlement.dto.response.SettlementSummaryResponse;
import me.nawa.settlement.dto.response.SettlementCandidateResponse;
import me.nawa.settlement.dto.response.SettlementParticipantResponse;
import me.nawa.settlement.dto.response.SettlementDetailResponse;
import me.nawa.settlement.dto.response.SettlementGameResultResponse;
import me.nawa.settlement.dto.response.SettlementGameResponse;
import me.nawa.settlement.dto.response.ReceiptAnalysisResponse;
import me.nawa.settlement.dto.response.ReceiptAnalysisItemResponse;
import me.nawa.settlement.dto.request.ReceiptItemUpdateRequest;
import me.nawa.settlement.dto.request.ReceiptItemRequest;
import me.nawa.settlement.dto.request.ReceiptAllocationUpdateRequest;
import me.nawa.settlement.dto.request.ReceiptAllocationRequest;
import me.nawa.settlement.dto.request.GameConsentRequest;
import me.nawa.settlement.dto.request.GameCreateRequest;
import me.nawa.settlement.exception.SettlementErrorCode;
import me.nawa.settlement.mapper.SettlementMapper;
import me.nawa.wallet.service.WalletTransferService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import java.util.Collections;
import java.util.UUID;

/**
 * 정산 서비스 구현체
 *
 * 정산 서비스 계약을 구현하기 위해 정산 영속성 계층을 사용합니다.
 */
@Service
@RequiredArgsConstructor
public class SettlementServiceImpl implements SettlementService {

    private final SettlementMapper settlementMapper;
    private final WalletTransferService walletTransferService;

    @Override
    @Transactional(readOnly = true)
    public SettlementListResponse getSettlements(Long memberId) {
        //1. 받은 요청은 내 부담금, 보낸 요청은 아직 회수할 총액을 각각 보여 준다.
        return SettlementListResponse.builder()
            .received(toSummaryResponses(settlementMapper.findReceivedSummaries(memberId)))
            .sent(toSummaryResponses(settlementMapper.findSentSummaries(memberId)))
            .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SettlementCandidateResponse> getCandidates(Long memberId) {
        //1. 이미 정산으로 전환된 원거래는 제외하고, 생성자가 결제한 완료 거래만 후보로 조회한다.
        return settlementMapper.findCandidateSources(memberId).stream().map(source ->
            SettlementCandidateResponse.builder()
                .transferId(source.getTransferId()).journeyName(source.getJourneyName())
                .gatheringName(source.getGatheringName()).merchantName(source.getMerchantName())
                .amount(source.getAmount()).paidAt(source.getPaidAt()).payerName(source.getPayerName())
                .participants(toParticipantResponses(settlementMapper.findParticipants(source.getAppointmentId())))
                .build()
        ).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SettlementDetailResponse getSettlement(Long memberId, Long settlementId) {
        //1. 생성자 또는 정산 참여자만 상세를 볼 수 있도록 조회 자체에 권한 조건을 둔다.
        SettlementDetail detail = settlementMapper.findDetail(settlementId, memberId);
        if (detail == null) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_NOT_FOUND);
        }
        return SettlementDetailResponse.builder()
            .id(detail.getSettlementId()).type(detail.getSplitMethod()).amount(detail.getTotalAmount())
            .status(detail.getSettlementStatus()).requestedBy(detail.getRequestedBy())
            .gatheringName(detail.getGatheringName()).merchantName(detail.getMerchantName())
            .items(settlementMapper.findItemNames(settlementId)).transactionId(detail.getTransactionNumber())
            .paidBy(detail.getPaidBy()).build();
    }

    @Override
    @Transactional
    public ReceiptAnalysisResponse analyzeReceipt(Long memberId, Long sourceTransferId, MultipartFile file) {
        //1. OCR 후속 단계에서는 파일을 분석하지 않고, 원거래에 연결된 DRAFT 분석만 만든다.
        if (file == null || file.isEmpty() || file.getOriginalFilename() == null
            || file.getOriginalFilename().isBlank()) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_RECEIPT_INVALID);
        }
        SettlementSource source = settlementMapper.findSourceForCreate(sourceTransferId, memberId);
        if (source == null) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_SOURCE_NOT_FOUND);
        }
        ReceiptAnalysis analysis = new ReceiptAnalysis(
            null, sourceTransferId, source.getAppointmentId(), memberId, file.getOriginalFilename(), "DRAFT", BigDecimal.ZERO
        );
        settlementMapper.insertReceiptAnalysis(analysis);
        return ReceiptAnalysisResponse.builder()
            .receiptAnalysisId(analysis.getReceiptAnalysisId()).recognizedTotal(BigDecimal.ZERO).items(List.of()).build();
    }

    @Override
    @Transactional
    public ReceiptAnalysisResponse updateReceiptItems(Long memberId, Long receiptAnalysisId, ReceiptItemUpdateRequest request) {
        //1. 분석 생성자만 DRAFT 항목을 교체할 수 있으며, 빈 목록은 유효한 정산 항목이 아니다.
        ReceiptAnalysis analysis = settlementMapper.findReceiptAnalysisForUpdate(receiptAnalysisId);
        if (analysis == null || !memberId.equals(analysis.getCreatedByMemberId()) || !"DRAFT".equals(analysis.getAnalysisStatus())
            || request == null || request.getItems() == null || request.getItems().isEmpty()) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_RECEIPT_INVALID);
        }
        List<ReceiptAnalysisItem> items = new java.util.ArrayList<>();
        short order = 1;
        for (ReceiptItemRequest requestItem : request.getItems()) {
            if (requestItem == null || requestItem.getName() == null || requestItem.getName().isBlank()
                || requestItem.getQuantity() == null || requestItem.getQuantity().signum() <= 0
                || requestItem.getUnitPrice() == null || requestItem.getUnitPrice().signum() < 0) {
                throw new BusinessException(SettlementErrorCode.SETTLEMENT_RECEIPT_INVALID);
            }
            BigDecimal lineTotal = requestItem.getUnitPrice().multiply(requestItem.getQuantity());
            items.add(new ReceiptAnalysisItem(null, receiptAnalysisId, requestItem.getName().trim(),
                requestItem.getUnitPrice(), requestItem.getQuantity(), lineTotal, order++));
        }
        BigDecimal total = items.stream().map(ReceiptAnalysisItem::getLineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        settlementMapper.deleteReceiptItems(receiptAnalysisId);
        settlementMapper.insertReceiptItems(items);
        settlementMapper.updateReceiptTotal(receiptAnalysisId, total);
        return ReceiptAnalysisResponse.builder().receiptAnalysisId(receiptAnalysisId).recognizedTotal(total)
            .items(items.stream().map(item -> ReceiptAnalysisItemResponse.builder().id(item.getReceiptAnalysisItemId())
                .name(item.getItemName()).quantity(item.getQuantity()).unitPrice(item.getUnitPrice()).build()).toList()).build();
    }

    @Override
    @Transactional
    public void updateReceiptAllocations(Long memberId, Long receiptAnalysisId, ReceiptAllocationUpdateRequest request) {
        //1. DRAFT 분석의 생성자만 배분을 확정할 수 있다.
        ReceiptAnalysis analysis = settlementMapper.findReceiptAnalysisForUpdate(receiptAnalysisId);
        if (analysis == null || !memberId.equals(analysis.getCreatedByMemberId()) || !"DRAFT".equals(analysis.getAnalysisStatus())
            || request == null || request.getAllocations() == null || request.getAllocations().isEmpty()) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_RECEIPT_INVALID);
        }
        java.util.Map<Long, ReceiptAnalysisItem> items = settlementMapper.findReceiptItemsForUpdate(receiptAnalysisId).stream()
            .collect(java.util.stream.Collectors.toMap(ReceiptAnalysisItem::getReceiptAnalysisItemId, item -> item));
        java.util.Map<Long, Long> appointmentMembers = settlementMapper.findActiveMembers(analysis.getAppointmentId()).stream()
            .collect(java.util.stream.Collectors.toMap(SettlementMember::getMemberId, SettlementMember::getAppointmentMemberId));
        java.util.Map<Long, BigDecimal> quantities = new java.util.HashMap<>();
        List<ReceiptItemAllocation> allocations = new java.util.ArrayList<>();
        for (ReceiptAllocationRequest requestAllocation : request.getAllocations()) {
            ReceiptAnalysisItem item = requestAllocation == null ? null : items.get(requestAllocation.getItemId());
            Long appointmentMemberId = requestAllocation == null ? null : appointmentMembers.get(requestAllocation.getParticipantId());
            if (item == null || appointmentMemberId == null || requestAllocation.getQuantity() == null
                || requestAllocation.getQuantity().signum() <= 0) {
                throw new BusinessException(SettlementErrorCode.SETTLEMENT_RECEIPT_INVALID);
            }
            quantities.merge(item.getReceiptAnalysisItemId(), requestAllocation.getQuantity(), BigDecimal::add);
            BigDecimal amount = item.getUnitPrice().multiply(requestAllocation.getQuantity());
            allocations.add(new ReceiptItemAllocation(item.getReceiptAnalysisItemId(), appointmentMemberId,
                requestAllocation.getQuantity(), amount));
        }
        if (items.size() != quantities.size() || items.values().stream().anyMatch(item ->
            item.getQuantity().compareTo(quantities.getOrDefault(item.getReceiptAnalysisItemId(), BigDecimal.ZERO)) != 0)) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_RECEIPT_INVALID);
        }
        //2. 모든 항목 수량이 정확히 배분된 경우에만 기존 배분을 교체하고 ALLOCATED로 전환한다.
        settlementMapper.deleteReceiptAllocations(receiptAnalysisId);
        settlementMapper.insertReceiptAllocations(allocations);
        settlementMapper.markReceiptAllocated(receiptAnalysisId);
    }

    @Override
    @Transactional
    public void submitGameConsent(Long memberId, Long settlementId, GameConsentRequest request) {
        //1. 게임이 동의 대기 상태일 때만 참여자가 자신의 PENDING 동의를 한 번 제출할 수 있다.
        if (request == null || !("AGREED".equals(request.getStatus()) || "DECLINED".equals(request.getStatus()))) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_GAME_INVALID);
        }
        SettlementGame game = settlementMapper.findSettlementGameForUpdate(settlementId);
        if (game == null || !"WAITING_CONSENT".equals(game.getGameStatus())
            || settlementMapper.updateGameConsent(settlementId, memberId, request.getStatus()) != 1) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_GAME_INVALID);
        }
    }

    @Override
    @Transactional
    public void startGame(Long memberId, Long settlementId) {
        //1. 생성자만 전원 동의가 끝난 동의 대기 게임을 시작할 수 있다.
        Settlement settlement = settlementMapper.findByIdForUpdate(settlementId);
        SettlementGame game = settlementMapper.findSettlementGameForUpdate(settlementId);
        if (settlement == null || game == null || !memberId.equals(settlement.getCreatedByMemberId())
            || !"DRAFT".equals(settlement.getSettlementStatus()) || !"WAITING_CONSENT".equals(game.getGameStatus())) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_GAME_INVALID);
        }
        List<SettlementGameMember> members = settlementMapper.findGameMembersForUpdate(settlementId);
        if (members.stream().anyMatch(member -> !"AGREED".equals(member.getConsentStatus()))) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_GAME_INVALID);
        }
        List<SettlementGameMember> candidates = new ArrayList<>(members.stream()
            .filter(member -> !settlement.getPayerMemberId().equals(member.getMemberId())).toList());
        if (game.getLiableCount() > candidates.size()) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_GAME_INVALID);
        }
        String seed = UUID.randomUUID().toString();
        Collections.shuffle(candidates, new java.util.Random(seed.hashCode()));
        List<SettlementGameMember> liable = candidates.subList(0, game.getLiableCount());
        BigDecimal amount = settlement.getTotalAmount().divide(BigDecimal.valueOf(game.getLiableCount()), 2, java.math.RoundingMode.DOWN);
        BigDecimal receivable = amount.multiply(BigDecimal.valueOf(game.getLiableCount()));
        BigDecimal payerShare = settlement.getTotalAmount().subtract(receivable);
        settlementMapper.assignGameLiables(settlementId, liable.stream().map(SettlementGameMember::getAppointmentMemberId).toList());
        List<SettlementMember> paymentMembers = new ArrayList<>();
        for (SettlementGameMember member : members) {
            boolean isPayer = settlement.getPayerMemberId().equals(member.getMemberId());
            boolean isLiable = liable.stream().anyMatch(selected -> selected.getMemberId().equals(member.getMemberId()));
            paymentMembers.add(new SettlementMember(null, settlementId, member.getAppointmentMemberId(), member.getMemberId(),
                isLiable ? amount : (isPayer ? payerShare : BigDecimal.ZERO),
                isLiable ? "PENDING" : "NOT_REQUESTED", null));
        }
        //2. 부담자·금액·결제 상태를 한 트랜잭션에서 확정한 뒤에만 REQUESTED로 공개한다.
        settlementMapper.insertSettlementMembers(paymentMembers);
        settlementMapper.activateGameSettlement(settlementId, payerShare, receivable);
        settlementMapper.completeGame(settlementId, seed);
    }

    @Override
    @Transactional(readOnly = true)
    public SettlementGameResponse getGame(Long memberId, Long settlementId) {
        //1. 게임 참여자만 동의 진행 상황과 확정 결과를 조회할 수 있다.
        Settlement settlement = settlementMapper.findById(settlementId);
        SettlementGame game = settlementMapper.findSettlementGame(settlementId);
        List<SettlementGameMember> members = settlementMapper.findGameMembers(settlementId);
        if (settlement == null || game == null
            || members.stream().noneMatch(member -> memberId.equals(member.getMemberId()))) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_GAME_INVALID);
        }
        java.util.Map<Long, String> names = settlementMapper.findParticipants(settlement.getAppointmentId()).stream()
            .collect(java.util.stream.Collectors.toMap(SettlementParticipant::getMemberId, SettlementParticipant::getDisplayName));
        List<SettlementParticipantResponse> participants = members.stream()
            .map(member -> toGameParticipant(member, names)).toList();
        List<SettlementParticipantResponse> liable = members.stream()
            .filter(member -> Boolean.TRUE.equals(member.getLiable()))
            .map(member -> toGameParticipant(member, names)).toList();
        String declinedBy = members.stream().filter(member -> "DECLINED".equals(member.getConsentStatus()))
            .map(member -> names.get(member.getMemberId())).filter(java.util.Objects::nonNull).findFirst().orElse(null);

        return SettlementGameResponse.builder()
            .id(settlementId).gameType(game.getGameType()).amount(settlement.getTotalAmount())
            .liableCount(game.getLiableCount()).participants(participants)
            .agreementCount((int) members.stream().filter(member -> "AGREED".equals(member.getConsentStatus())).count())
            .lifecycle(game.getGameStatus())
            .viewerRole(memberId.equals(settlement.getCreatedByMemberId()) ? "CREATOR" : "PARTICIPANT")
            .declinedBy(declinedBy).journeyName(null).merchantName(null)
            .originalPayer(names.get(settlement.getPayerMemberId())).liableParticipants(liable)
            .transactionId(null).currentParticipantName(names.get(memberId)).build();
    }

    @Override
    @Transactional(readOnly = true)
    public SettlementGameResultResponse getGameResult(Long memberId, Long settlementId) {
        //1. 결과는 COMPLETED 게임의 참여자만 조회할 수 있으며, 확정 부담자는 DB is_liable 값이 정본이다.
        Settlement settlement = settlementMapper.findById(settlementId);
        SettlementGame game = settlementMapper.findSettlementGame(settlementId);
        List<SettlementGameMember> members = settlementMapper.findGameMembers(settlementId);
        if (settlement == null || game == null || !"COMPLETED".equals(game.getGameStatus())
            || members.stream().noneMatch(member -> memberId.equals(member.getMemberId()))) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_GAME_INVALID);
        }
        java.util.Map<Long, String> names = settlementMapper.findParticipants(settlement.getAppointmentId()).stream()
            .collect(java.util.stream.Collectors.toMap(SettlementParticipant::getMemberId, SettlementParticipant::getDisplayName));
        List<SettlementParticipantResponse> liable = members.stream().filter(member -> Boolean.TRUE.equals(member.getLiable()))
            .map(member -> SettlementParticipantResponse.builder().id(member.getMemberId()).name(names.get(member.getMemberId()))
                .initials(names.get(member.getMemberId()) == null ? "?" : names.get(member.getMemberId()).substring(0, 1))
                .consentStatus(member.getConsentStatus()).build()).toList();
        return SettlementGameResultResponse.builder().settlementId(settlementId)
            .amount(settlement.getTotalAmount().divide(BigDecimal.valueOf(game.getLiableCount()), 2, java.math.RoundingMode.DOWN))
            .liableParticipants(liable).build();
    }

    @Override
    @Transactional
    public SettlementCreateResponse createSettlement(Long memberId, CreateSettlementRequest request) {
        //1. 원거래의 결제자와 약속 문맥을 잠금 없이 확인한다. 생성자는 원결제자여야 한다.
        if (request == null || request.getSourceTransferId() == null || request.getParticipantIds() == null
            || !("EQUAL".equals(request.getType()) || "ITEMIZED".equals(request.getType()) || "GAME".equals(request.getType()))) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_CREATE_INVALID);
        }
        SettlementSource source = settlementMapper.findSourceForCreate(request.getSourceTransferId(), memberId);
        if (source == null) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_SOURCE_NOT_FOUND);
        }
        if ("ITEMIZED".equals(request.getType())) {
            return createItemizedSettlement(memberId, request, source);
        }
        if ("GAME".equals(request.getType())) {
            return createGameSettlement(memberId, request, source);
        }
        List<SettlementMember> members = settlementMapper.findActiveMembers(source.getAppointmentId());
        Set<Long> requested = Set.copyOf(request.getParticipantIds());
        if (requested.size() != members.size() || !members.stream().map(SettlementMember::getMemberId).collect(java.util.stream.Collectors.toSet()).equals(requested)) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_CREATE_INVALID);
        }

        //2. 균등 분할의 나머지는 원결제자 부담으로 둬 총액과 참여자 부담금의 합을 정확히 보존한다.
        BigDecimal unit = source.getAmount().divide(BigDecimal.valueOf(members.size()), 2, java.math.RoundingMode.DOWN);
        BigDecimal payerShare = source.getAmount().subtract(unit.multiply(BigDecimal.valueOf(members.size() - 1)));
        BigDecimal receivable = source.getAmount().subtract(payerShare);
        Settlement settlement = Settlement.builder()
            .appointmentId(source.getAppointmentId()).createdByMemberId(memberId).payerMemberId(source.getPayerMemberId())
            .sourceTransferId(source.getTransferId()).settlementStatus("REQUESTED").splitMethod("EQUAL")
            .totalAmount(source.getAmount()).payerShareAmount(payerShare).receivableAmount(receivable)
            .requestedAt(LocalDateTime.now()).build();
        settlementMapper.insertSettlement(settlement);

        //3. 원결제자는 NOT_REQUESTED, 나머지 참여자는 실제 지갑 결제 대상 PENDING으로 저장한다.
        members.forEach(member -> {
            member.setSettlementId(settlement.getSettlementId());
            boolean payer = memberId.equals(member.getMemberId());
            member.setShareAmount(payer ? payerShare : unit);
            member.setRequestStatus(payer ? "NOT_REQUESTED" : "PENDING");
        });
        settlementMapper.insertSettlementMembers(members);
        return SettlementCreateResponse.builder().id(settlement.getSettlementId()).build();
    }

    private SettlementCreateResponse createItemizedSettlement(Long memberId, CreateSettlementRequest request,
            SettlementSource source) {
        //1. 원거래와 일치하는 ALLOCATED 분석만 한 번 ITEMIZED 정산으로 전환할 수 있다.
        if (request.getReceiptAnalysisId() == null) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_CREATE_INVALID);
        }
        ReceiptAnalysis analysis = settlementMapper.findReceiptAnalysisForUpdate(request.getReceiptAnalysisId());
        if (analysis == null || !"ALLOCATED".equals(analysis.getAnalysisStatus())
            || !source.getTransferId().equals(analysis.getSourceTransferId())
            || !memberId.equals(analysis.getCreatedByMemberId())) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_CREATE_INVALID);
        }
        List<ReceiptAllocationView> allocations = settlementMapper.findReceiptAllocationViews(analysis.getReceiptAnalysisId());
        java.util.Map<Long, BigDecimal> amounts = allocations.stream().collect(java.util.stream.Collectors
            .toMap(ReceiptAllocationView::getMemberId, ReceiptAllocationView::getAllocatedAmount));
        if (!Set.copyOf(request.getParticipantIds()).equals(amounts.keySet())
            || amounts.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(analysis.getRecognizedTotal()) != 0) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_CREATE_INVALID);
        }
        BigDecimal payerShare = amounts.getOrDefault(memberId, BigDecimal.ZERO);
        Settlement settlement = Settlement.builder().appointmentId(source.getAppointmentId()).createdByMemberId(memberId)
            .payerMemberId(source.getPayerMemberId()).sourceTransferId(source.getTransferId()).settlementStatus("REQUESTED")
            .splitMethod("ITEMIZED").totalAmount(analysis.getRecognizedTotal()).payerShareAmount(payerShare)
            .receivableAmount(analysis.getRecognizedTotal().subtract(payerShare)).requestedAt(LocalDateTime.now()).build();
        settlementMapper.insertSettlement(settlement);
        List<SettlementMember> members = settlementMapper.findActiveMembers(source.getAppointmentId()).stream()
            .filter(member -> amounts.containsKey(member.getMemberId())).peek(member -> {
                member.setSettlementId(settlement.getSettlementId()); member.setShareAmount(amounts.get(member.getMemberId()));
                member.setRequestStatus(memberId.equals(member.getMemberId()) ? "NOT_REQUESTED" : "PENDING");
            }).toList();
        settlementMapper.insertSettlementMembers(members);
        //2. 항목과 항목별 배분을 정산 스냅샷으로 복제해 영수증 수정과 무관하게 결과를 재현한다.
        settlementMapper.copyReceiptItemsToSettlement(analysis.getReceiptAnalysisId(), settlement.getSettlementId());
        settlementMapper.copyReceiptItemSharesToSettlement(analysis.getReceiptAnalysisId(), settlement.getSettlementId());
        settlementMapper.markReceiptUsed(analysis.getReceiptAnalysisId());
        return SettlementCreateResponse.builder().id(settlement.getSettlementId()).build();
    }

    private SettlementCreateResponse createGameSettlement(Long memberId, CreateSettlementRequest request,
            SettlementSource source) {
        //1. GAME은 활성 약속 참여자 전원이 동의 대기 대상이며, 원결제자는 부담자가 될 수 없다.
        GameCreateRequest gameRequest = request.getGame();
        List<SettlementMember> participants = settlementMapper.findActiveMembers(source.getAppointmentId());
        if (gameRequest == null || gameRequest.getType() == null || gameRequest.getType().isBlank()
            || gameRequest.getLiableCount() == null || gameRequest.getLiableCount() <= 0
            || gameRequest.getLiableCount() >= participants.size()
            || !Set.copyOf(request.getParticipantIds()).equals(participants.stream()
                .map(SettlementMember::getMemberId).collect(java.util.stream.Collectors.toSet()))) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_GAME_INVALID);
        }
        Settlement settlement = Settlement.builder().appointmentId(source.getAppointmentId()).createdByMemberId(memberId)
            .payerMemberId(source.getPayerMemberId()).sourceTransferId(source.getTransferId()).settlementStatus("DRAFT")
            .splitMethod("GAME").totalAmount(source.getAmount()).payerShareAmount(source.getAmount())
            .receivableAmount(BigDecimal.ZERO).requestedAt(null).build();
        settlementMapper.insertSettlement(settlement);
        settlementMapper.insertSettlementGame(new SettlementGame(settlement.getSettlementId(), gameRequest.getType(),
            gameRequest.getLiableCount(), "WAITING_CONSENT", null));
        settlementMapper.insertSettlementGameMembers(participants.stream().map(member -> new SettlementGameMember(
            settlement.getSettlementId(), member.getAppointmentMemberId(), member.getMemberId(), "PENDING", false
        )).toList());
        return SettlementCreateResponse.builder().id(settlement.getSettlementId()).build();
    }

    @Override
    @Transactional
    public void paySettlement(Long memberId, Long settlementId) {
        //1. 정산과 참여자 행을 잠근다. 중복 결제가 들어와도 한 요청만 PENDING 상태를 소비한다.
        Settlement settlement = settlementMapper.findByIdForUpdate(settlementId);
        if (settlement == null) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_NOT_FOUND);
        }
        if (!"REQUESTED".equals(settlement.getSettlementStatus())) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_PAYMENT_NOT_ALLOWED);
        }

        //2. 요청자가 실제 부담자인지, 아직 결제 가능한 상태인지 확인한다.
        SettlementMember payment = settlementMapper
            .findMembersBySettlementIdForUpdate(settlementId)
            .stream()
            .filter(member -> memberId.equals(member.getMemberId()))
            .findFirst()
            .orElseThrow(() -> new BusinessException(SettlementErrorCode.SETTLEMENT_PAYMENT_NOT_FOUND));
        if (!"PENDING".equals(payment.getRequestStatus())
            || payment.getShareAmount() == null || payment.getShareAmount().signum() <= 0) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_PAYMENT_NOT_ALLOWED);
        }

        //3. 지갑 원장이 소유한 실제 이체를 먼저 완료하고, 생성된 거래 ID를 정산 참여자에 연결한다.
        long transferId = walletTransferService.transfer(
            memberId,
            memberId,
            settlement.getPayerMemberId(),
            payment.getShareAmount(),
            "Settlement #" + settlementId
        );

        //4. 조건부 갱신이 실패하면 이미 처리된 경쟁 요청이므로 전체 트랜잭션을 롤백한다.
        if (settlementMapper.markSettlementMemberPaid(payment.getSettlementMemberId(), transferId) != 1) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_PAYMENT_NOT_ALLOWED);
        }
        //5. 마지막 PENDING 부담금까지 결제되면 정산을 완료 상태로 전환한다.
        settlementMapper.completeSettlementIfNoPendingPayments(settlementId);
    }

    @Override
    @Transactional
    public void cancelSettlement(Long memberId, Long settlementId) {
        //1. 원결제자만, 아직 누구도 결제하지 않은 REQUESTED 정산을 취소할 수 있다.
        Settlement settlement = settlementMapper.findByIdForUpdate(settlementId);
        if (settlement == null) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_NOT_FOUND);
        }
        if (!memberId.equals(settlement.getCreatedByMemberId())
            || !"REQUESTED".equals(settlement.getSettlementStatus())
            || settlementMapper.cancelSettlement(settlementId, memberId) != 1) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_CANCEL_NOT_ALLOWED);
        }
    }

    private List<SettlementSummaryResponse> toSummaryResponses(List<SettlementSummary> summaries) {
        return summaries.stream().map(summary -> SettlementSummaryResponse.builder()
            .id(summary.getSettlementId()).title(summary.getTitle()).amount(summary.getAmount())
            .type(summary.getSplitMethod()).status(summary.getSettlementStatus()).build()).toList();
    }

    private List<SettlementParticipantResponse> toParticipantResponses(List<SettlementParticipant> participants) {
        return participants.stream().map(participant -> SettlementParticipantResponse.builder()
            .id(participant.getMemberId()).name(participant.getDisplayName())
            .initials(participant.getDisplayName() == null || participant.getDisplayName().isBlank()
                ? "?" : participant.getDisplayName().substring(0, 1).toUpperCase())
            .consentStatus(null).build()).toList();
    }

    private SettlementParticipantResponse toGameParticipant(SettlementGameMember member,
            java.util.Map<Long, String> names) {
        String name = names.get(member.getMemberId());
        return SettlementParticipantResponse.builder().id(member.getMemberId()).name(name)
            .initials(name == null || name.isBlank() ? "?" : name.substring(0, 1).toUpperCase())
            .consentStatus(member.getConsentStatus()).build();
    }
}
