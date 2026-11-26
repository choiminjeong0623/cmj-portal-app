package com.cmj.portal.admin.notify;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.cmj.common.security.SecurityUtil;
import com.cmj.common.security.SessionRefreshService;
import com.cmj.portal.label.LabelService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 여러 사용자에게 한 역할을 한꺼번에 주거나 빼는 변경 (사람 단위 대량 변경).
 *
 * 🟩 관리자 화면 쪽은 본인이 수정에 참여해 맡았다고 기억함 — 본인 역할 범위는 기억 정리 중, 세부는 예시
 * 📘 개선안 (실제로 한 일인지 미확인):
 *   - 미리보기: 저장 전에 실제로 영향받는 인원을 세어 "N명이 영향받습니다" 확인창
 *   - 기준(정책 라벨 BULK_NOTIFY_LIMIT)을 넘으면 바로 반영하지 않고 TB_ROLE_CHANGE_REQ 에 PENDING
 *   - 요청자와 다른 관리자가 승인해야 반영 + 알림
 */
@Service
public class RoleChangeRequestService {

    private static final int DEFAULT_LIMIT = 50; // ⚠️ 예시 값 — 정책 라벨이 없을 때만

    private final NamedParameterJdbcTemplate jdbc;
    private final LabelService labels;
    private final ApplicationEventPublisher events;
    private final SessionRefreshService refresh;

    public RoleChangeRequestService(NamedParameterJdbcTemplate jdbc, LabelService labels,
                                    ApplicationEventPublisher events, SessionRefreshService refresh) {
        this.jdbc = jdbc;
        this.labels = labels;
        this.events = events;
        this.refresh = refresh;
    }

    public record Preview(int affectedCount, int limit, boolean needsApproval) {}

    public record Result(String changeId, String status /* APPLIED / PENDING */, int affectedCount) {}

    /** 📘 미리보기: 실제로 바뀌는 사람만 센다 (GRANT는 아직 없는 사람, REVOKE는 지금 가진 사람) */
    public Preview preview(String roleId, String action, List<String> userIds) {
        int affected = affectedUsers(roleId, action, userIds).size();
        int limit = labels.policyInt("BULK_NOTIFY_LIMIT", DEFAULT_LIMIT);
        return new Preview(affected, limit, affected > limit);
    }

    /** 저장: 기준 이하면 바로 반영, 넘으면 승인 대기 */
    @Transactional
    public Result request(String roleId, String action, List<String> userIds) {
        validateAction(action);
        List<String> targets = affectedUsers(roleId, action, userIds);
        String changeId = UUID.randomUUID().toString();
        String adminId = SecurityUtil.getUserId();
        int limit = labels.policyInt("BULK_NOTIFY_LIMIT", DEFAULT_LIMIT);

        if (targets.size() > limit) {
            jdbc.update("""
                INSERT INTO TB_ROLE_CHANGE_REQ (CHANGE_ID, ROLE_ID, ACTION, TARGET_CNT, STATUS, REQ_ID, REQ_DT)
                VALUES (:c, :r, :a, :n, 'PENDING', :admin, SYSTIMESTAMP)""",
                Map.of("c", changeId, "r", roleId, "a", action, "n", targets.size(), "admin", adminId));
            saveTargets(changeId, targets);
            return new Result(changeId, "PENDING", targets.size());
        }
        apply(changeId, roleId, action, targets, adminId);
        return new Result(changeId, "APPLIED", targets.size());
    }

    /** 📘 승인: 요청자와 다른 관리자만. 승인 시점에 실제 반영 + 알림 */
    @Transactional
    public Result approve(String changeId) {
        String approver = SecurityUtil.getUserId();
        Map<String, Object> req = jdbc.queryForList("""
            SELECT ROLE_ID, ACTION, REQ_ID, STATUS FROM TB_ROLE_CHANGE_REQ
             WHERE CHANGE_ID = :c FOR UPDATE""", Map.of("c", changeId))
            .stream().findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "변경 요청이 없습니다."));
        if (!"PENDING".equals(req.get("STATUS"))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 처리된 요청입니다.");
        }
        if (approver.equals(req.get("REQ_ID"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "요청한 관리자는 직접 승인할 수 없습니다.");
        }
        List<String> targets = jdbc.queryForList(
            "SELECT USER_ID FROM TB_ROLE_CHANGE_REQ_USER WHERE CHANGE_ID = :c", Map.of("c", changeId), String.class);
        jdbc.update("""
            UPDATE TB_ROLE_CHANGE_REQ SET STATUS = 'APPROVED', APPR_ID = :admin, APPR_DT = SYSTIMESTAMP
             WHERE CHANGE_ID = :c""", Map.of("c", changeId, "admin", approver));
        apply(changeId, (String) req.get("ROLE_ID"), (String) req.get("ACTION"), targets, approver);
        return new Result(changeId, "APPLIED", targets.size());
    }

    /** 실제 반영 → 커밋 뒤 이벤트로 알림(발송 대기 테이블) + 대상 사용자 세션 갱신 */
    private void apply(String changeId, String roleId, String action, List<String> targets, String adminId) {
        for (String userId : targets) {
            if ("GRANT".equals(action)) {
                jdbc.update("""
                    INSERT INTO TB_USER_ROLE (USER_ID, ROLE_ID, REG_ID, REG_DT)
                    VALUES (:u, :r, :admin, SYSTIMESTAMP)""", Map.of("u", userId, "r", roleId, "admin", adminId));
            } else {
                jdbc.update("DELETE FROM TB_USER_ROLE WHERE USER_ID = :u AND ROLE_ID = :r",
                    Map.of("u", userId, "r", roleId));
            }
        }
        events.publishEvent(new RoleChangeEvents.UserRoleChanged(changeId, roleId, targets, action, adminId));
        // 사람 단위 변경은 대상자 수가 기준 이하일 때만 바로 오므로, 대상자 세션을 커밋 뒤 갱신 (✅ Q7 기존 방식)
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
            new org.springframework.transaction.support.TransactionSynchronization() {
                @Override public void afterCommit() { targets.forEach(refresh::refreshUser); }
            });
    }

    private List<String> affectedUsers(String roleId, String action, List<String> userIds) {
        validateAction(action);
        if (userIds == null || userIds.isEmpty()) return List.of();
        String sql = "GRANT".equals(action)
            ? """
              SELECT U.USER_ID FROM TB_USER U
               WHERE U.USER_ID IN (:ids)
                 AND NOT EXISTS (SELECT 1 FROM TB_USER_ROLE UR WHERE UR.USER_ID = U.USER_ID AND UR.ROLE_ID = :r)"""
            : """
              SELECT UR.USER_ID FROM TB_USER_ROLE UR
               WHERE UR.ROLE_ID = :r AND UR.USER_ID IN (:ids)""";
        // ⚠️ Oracle IN 목록은 1000개 제한 — 실제로는 임시 테이블이나 나눠 조회
        return jdbc.queryForList(sql, new MapSqlParameterSource().addValue("ids", userIds).addValue("r", roleId),
            String.class);
    }

    private void saveTargets(String changeId, List<String> targets) {
        jdbc.batchUpdate("""
            INSERT INTO TB_ROLE_CHANGE_REQ_USER (CHANGE_ID, USER_ID) VALUES (:c, :u)""",
            targets.stream().map(u -> new MapSqlParameterSource().addValue("c", changeId).addValue("u", u))
                .toArray(MapSqlParameterSource[]::new));
    }

    private static void validateAction(String action) {
        if (!"GRANT".equals(action) && !"REVOKE".equals(action)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "action 은 GRANT 또는 REVOKE");
        }
    }
}
