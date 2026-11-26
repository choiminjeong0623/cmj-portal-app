package com.cmj.portal.admin;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.cmj.common.security.SecurityUtil;
import com.cmj.common.security.SessionRefreshService;

import java.util.List;
import java.util.Map;

/**
 * 관리자 화면: 사용자·역할·권한·화면 매핑 관리 (✅ 사용자·권한 관리 전부 본인 담당, 대부분 DB + 자바 로직)
 *
 * ✅ 권한을 바꾸면 권한이 바뀐 "대상 사용자"의 세션을 갱신 → 다음 요청부터 바로 반영 (Q7)
 *    DB 저장이 확정(commit)된 뒤에 세션을 갱신해야 새 권한이 읽힌다 → afterCommit에서 실행
 * ✅ 권한 매핑을 하지 않은 화면은 화면 등록 그리드에 나타나지 않는 구조 (Q8) — 조회 쿼리는 ⚠️ 예시
 */
@Service
public class AdminPermissionService {

    private final NamedParameterJdbcTemplate jdbc;
    private final SessionRefreshService refresh;

    public AdminPermissionService(NamedParameterJdbcTemplate jdbc, SessionRefreshService refresh) {
        this.jdbc = jdbc;
        this.refresh = refresh;
    }

    /** 사용자에게 역할 부여 → 그 사용자 세션 갱신 */
    @Transactional
    public void saveUserRoles(String userId, List<String> roleIds) {
        jdbc.update("DELETE FROM TB_USER_ROLE WHERE USER_ID = :u", Map.of("u", userId));
        for (String roleId : roleIds) {
            jdbc.update("""
                INSERT INTO TB_USER_ROLE (USER_ID, ROLE_ID, REG_ID, REG_DT)
                VALUES (:u, :r, :admin, SYSTIMESTAMP)""",
                Map.of("u", userId, "r", roleId, "admin", SecurityUtil.getUserId()));
        }
        afterCommit(() -> refresh.refreshUser(userId));
    }

    /** 역할에 권한 묶기 → 그 역할을 가진 사용자 전체 세션 갱신 */
    @Transactional
    public void saveRolePermissions(String roleId, List<String> permIds) {
        jdbc.update("DELETE FROM TB_ROLE_PERM WHERE ROLE_ID = :r", Map.of("r", roleId));
        for (String permId : permIds) {
            jdbc.update("""
                INSERT INTO TB_ROLE_PERM (ROLE_ID, PERM_ID, REG_ID, REG_DT)
                VALUES (:r, :p, :admin, SYSTIMESTAMP)""",
                Map.of("r", roleId, "p", permId, "admin", SecurityUtil.getUserId()));
        }
        afterCommit(() -> refresh.refreshUsersByRole(roleId));
    }

    /** 권한에 화면 묶기 → 그 권한을 가진 사용자 전체 세션 갱신 */
    @Transactional
    public void savePermissionScreens(String permId, List<String> screenIds) {
        jdbc.update("DELETE FROM TB_PERM_SCREEN WHERE PERM_ID = :p", Map.of("p", permId));
        for (String screenId : screenIds) {
            jdbc.update("""
                INSERT INTO TB_PERM_SCREEN (PERM_ID, SCREEN_ID, REG_ID, REG_DT)
                VALUES (:p, :s, :admin, SYSTIMESTAMP)""",
                Map.of("p", permId, "s", screenId, "admin", SecurityUtil.getUserId()));
        }
        afterCommit(() -> refresh.refreshUsersByPermission(permId));
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
