package com.cmj.portal.admin;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.cmj.common.security.SecurityUtil;
import com.cmj.common.security.SessionRefreshService;
import com.cmj.portal.admin.notify.RoleChangeEvents;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 관리자 화면: 사용자·역할·권한·화면 매핑 관리 (✅ 사용자·권한 관리 전부 본인 담당, 대부분 DB + 자바 로직)
 *
 * ✅ 권한을 바꾸면 권한이 바뀐 "대상 사용자"의 세션을 갱신 → 다음 요청부터 바로 반영 (Q7)
 *    DB 저장이 확정(commit)된 뒤에 세션을 갱신해야 새 권한이 읽힌다 → afterCommit에서 실행
 * ✅ 권한 매핑을 하지 않은 화면은 화면 등록 그리드에 나타나지 않는 구조 (Q8) — 조회 쿼리는 ⚠️ 예시
 *
 * 역할 변경 알림 (2026-11-26):
 *   ✅ 역할 정의가 바뀌면(역할의 권한·화면) 개별 메일 대신 공지로 바꿨음 → RoleDefinitionChanged
 *   ✅ 사람 단위 변경 알림은 발송 대기 테이블로 → UserRoleChanged (본인이 만듦, 세부는 예시)
 *   ✅ 부여(GRANT)는 기존처럼 개인 메일, 회수(REVOKE)는 영향 범위 표시 + 50명 이상 관리자 승인
 *   이 서비스는 저장 후 이벤트만 발행하고, 공지·발송 대기 적재는 커밋 뒤 RoleChangeNotificationListener가 한다.
 *   여러 사용자에게 한 역할을 한꺼번에 주거나 빼는 변경은 RoleChangeRequestService (✅ 미리보기·승인)
 *   ✅ 장애 당시 대응: 역할 수정 버튼을 막고, 현업·개발자에게 수동 실행 금지를 안내함
 */
@Service
public class AdminPermissionService {

    private final NamedParameterJdbcTemplate jdbc;
    private final SessionRefreshService refresh;
    private final ApplicationEventPublisher events;

    public AdminPermissionService(NamedParameterJdbcTemplate jdbc, SessionRefreshService refresh,
                                  ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.refresh = refresh;
        this.events = events;
    }

    /** 사용자에게 역할 부여 → 그 사용자 세션 갱신 + 바뀐 역할만 그 사람에게 알림 */
    @Transactional
    public void saveUserRoles(String userId, List<String> roleIds) {
        List<String> before = jdbc.queryForList(
            "SELECT ROLE_ID FROM TB_USER_ROLE WHERE USER_ID = :u", Map.of("u", userId), String.class);
        jdbc.update("DELETE FROM TB_USER_ROLE WHERE USER_ID = :u", Map.of("u", userId));
        for (String roleId : roleIds) {
            jdbc.update("""
                INSERT INTO TB_USER_ROLE (USER_ID, ROLE_ID, REG_ID, REG_DT)
                VALUES (:u, :r, :admin, SYSTIMESTAMP)""",
                Map.of("u", userId, "r", roleId, "admin", SecurityUtil.getUserId()));
        }
        afterCommit(() -> refresh.refreshUser(userId));

        String admin = SecurityUtil.getUserId();
        roleIds.stream().filter(r -> !before.contains(r)).forEach(r -> events.publishEvent(
            new RoleChangeEvents.UserRoleChanged(UUID.randomUUID().toString(), r, List.of(userId), "GRANT", admin)));
        before.stream().filter(r -> !roleIds.contains(r)).forEach(r -> events.publishEvent(
            new RoleChangeEvents.UserRoleChanged(UUID.randomUUID().toString(), r, List.of(userId), "REVOKE", admin)));
    }

    /**
     * 역할에 권한 묶기 = 역할 정의 변경
     * ✅ 개별 메일 대신 공지 (RoleDefinitionChanged)
     * 📘 개선안: 그 역할을 가진 사용자 전체 세션을 한꺼번에 다시 만들지 않고, 권한 버전만 올림 (리스너가 bump)
     */
    @Transactional
    public void saveRolePermissions(String roleId, List<String> permIds) {
        jdbc.update("DELETE FROM TB_ROLE_PERM WHERE ROLE_ID = :r", Map.of("r", roleId));
        for (String permId : permIds) {
            jdbc.update("""
                INSERT INTO TB_ROLE_PERM (ROLE_ID, PERM_ID, REG_ID, REG_DT)
                VALUES (:r, :p, :admin, SYSTIMESTAMP)""",
                Map.of("r", roleId, "p", permId, "admin", SecurityUtil.getUserId()));
        }
        // 예전 방식: afterCommit(() -> refresh.refreshUsersByRole(roleId)); — 대상이 수백 명이면 일괄 갱신 부담
        events.publishEvent(new RoleChangeEvents.RoleDefinitionChanged(UUID.randomUUID().toString(),
            List.of(roleId), SecurityUtil.getUserId(), "역할 " + roleId + "의 권한이 변경되었습니다.")); // ⚠️ 예시 문구
    }

    /**
     * 권한에 화면 묶기 = 이 권한을 쓰는 역할들의 정의 변경
     * ✅ 개별 메일 대신 공지 / 📘 개선안: 관련 역할들의 권한 버전만 올림
     */
    @Transactional
    public void savePermissionScreens(String permId, List<String> screenIds) {
        jdbc.update("DELETE FROM TB_PERM_SCREEN WHERE PERM_ID = :p", Map.of("p", permId));
        for (String screenId : screenIds) {
            jdbc.update("""
                INSERT INTO TB_PERM_SCREEN (PERM_ID, SCREEN_ID, REG_ID, REG_DT)
                VALUES (:p, :s, :admin, SYSTIMESTAMP)""",
                Map.of("p", permId, "s", screenId, "admin", SecurityUtil.getUserId()));
        }
        // 예전 방식: afterCommit(() -> refresh.refreshUsersByPermission(permId));
        List<String> roleIds = new ArrayList<>(jdbc.queryForList(
            "SELECT ROLE_ID FROM TB_ROLE_PERM WHERE PERM_ID = :p", Map.of("p", permId), String.class));
        if (!roleIds.isEmpty()) {
            events.publishEvent(new RoleChangeEvents.RoleDefinitionChanged(UUID.randomUUID().toString(),
                roleIds, SecurityUtil.getUserId(), "권한 " + permId + "의 화면 구성이 변경되었습니다.")); // ⚠️ 예시 문구
        }
    }

    /** 화면 등록 그리드: 권한 매핑이 된 화면만 나온다 (✅ 매핑을 안 하면 그리드에 안 뜸) */
    public List<Map<String, Object>> findRegisteredScreens(String portalCd) {
        return jdbc.queryForList("""
            SELECT S.SCREEN_ID, S.UP_SCREEN_ID, S.MENU_NM, S.API_PATH, S.SORT_ORD, S.USE_YN
              FROM TB_SCREEN S
             WHERE S.PORTAL_CD = :portal
               AND EXISTS (SELECT 1 FROM TB_PERM_SCREEN PS WHERE PS.SCREEN_ID = S.SCREEN_ID)
             ORDER BY S.SORT_ORD""", new MapSqlParameterSource("portal", portalCd));
    }

    /** ✅ 관리자 화면에서 사용자별 MFA Y/N */
    public void setUserMfa(String userId, String mfaYn) {
        jdbc.update("""
            UPDATE TB_USER SET MFA_YN = :yn, UPD_ID = :admin, UPD_DT = SYSTIMESTAMP
             WHERE USER_ID = :u""",
            Map.of("yn", "Y".equals(mfaYn) ? "Y" : "N", "u", userId, "admin", SecurityUtil.getUserId()));
    }

    private static void afterCommit(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
