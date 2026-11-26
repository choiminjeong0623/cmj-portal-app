package com.cmj.portal.admin.notify;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import com.cmj.common.security.PermissionVersionStore;

/**
 * 커밋이 성공한 뒤에만 알림을 만든다 (AFTER_COMMIT) → 저장이 롤백되면 공지·메일도 없음.
 * AFTER_COMMIT 시점에는 원래 트랜잭션이 끝났으므로 새 트랜잭션(REQUIRES_NEW)으로 적는다.
 */
@Component
public class RoleChangeNotificationListener {

    private final NamedParameterJdbcTemplate jdbc;
    private final NotificationOutbox outbox;
    private final PermissionVersionStore versions;

    public RoleChangeNotificationListener(NamedParameterJdbcTemplate jdbc, NotificationOutbox outbox,
                                          PermissionVersionStore versions) {
        this.jdbc = jdbc;
        this.outbox = outbox;
        this.versions = versions;
    }

    /**
     * ✅ 역할 정의가 바뀌면 개별 메일 대신 공지로 바꿨음 (2026-11-26)
     * 🟩 본인 기억: 그 역할들의 권한 버전만 올림 → 사용자 세션은 각자 다음 요청 때 다시 만들어짐
     *    (예전 방식: 그 역할을 가진 사용자 전체의 세션을 한꺼번에 다시 만듦 — 수백 명이면 부담)
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(RoleChangeEvents.RoleDefinitionChanged e) {
        jdbc.update("""
            INSERT INTO TB_NOTICE (NOTICE_ID, NOTICE_TYPE, TITLE, CONTENT, REF_ID, POST_YN, REG_ID, REG_DT)
            VALUES (:id, 'ROLE_CHANGE', :title, :content, :ref, 'Y', :admin, SYSTIMESTAMP)""",
            new MapSqlParameterSource()
                .addValue("id", e.changeId())
                .addValue("title", "권한 변경 안내")                       // ⚠️ 문구는 예시 (실제로는 다국어 라벨)
                .addValue("content", e.summary())
                .addValue("ref", String.join(",", e.roleIds()))
                .addValue("admin", e.adminId()));
        e.roleIds().forEach(versions::bump);
    }

    /**
     * 사람 단위 변경 → 발송 대기 테이블에 대상자별로 한 줄씩 (✅ 커밋된 메일을 바로 보내지 않고 대기 테이블에 넣음 — 세부 구조는 예시)
     *  - GRANT: ✅ 부여는 기존처럼 개인 메일 (예시에서는 같은 대기 테이블을 거침 — 실제 발송 경로는 미확인)
     *  - REVOKE: ✅ 회수 50명 이상은 승인 전까지 여기로 오지 않음 (RoleChangeRequestService가 저장 전에 승인 대기로 돌림)
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(RoleChangeEvents.UserRoleChanged e) {
        String title = "GRANT".equals(e.action()) ? "역할이 부여되었습니다" : "역할이 회수되었습니다"; // ⚠️ 예시 문구
        outbox.enqueue(e.changeId(), e.userIds(), "ROLE_" + e.action(), title, "ROLE_ID=" + e.roleId());
    }
}
