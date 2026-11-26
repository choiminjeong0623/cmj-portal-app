package com.cmj.portal.admin;

import org.springframework.web.bind.annotation.*;
import com.cmj.portal.label.LabelService;

import java.util.List;
import java.util.Map;

/**
 * 관리자 API. /api/admin/** 은 관리자 화면(TB_SCREEN.API_PATH)에 매핑된 권한이 있는 사용자만
 * SessionAuthFilter를 통과한다 → 납세자가 주소를 직접 쳐도 403 (✅ Q7 서버 차단)
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminPermissionService permissions;
    private final LabelService labels;

    public AdminController(AdminPermissionService permissions, LabelService labels) {
        this.permissions = permissions;
        this.labels = labels;
    }

    @PutMapping("/users/{userId}/roles")
    public void saveUserRoles(@PathVariable String userId, @RequestBody List<String> roleIds) {
        permissions.saveUserRoles(userId, roleIds);
    }

    @PutMapping("/roles/{roleId}/permissions")
    public void saveRolePermissions(@PathVariable String roleId, @RequestBody List<String> permIds) {
        permissions.saveRolePermissions(roleId, permIds);
    }

    @PutMapping("/permissions/{permId}/screens")
    public void savePermissionScreens(@PathVariable String permId, @RequestBody List<String> screenIds) {
        permissions.savePermissionScreens(permId, screenIds);
    }

    @GetMapping("/screens")
    public List<Map<String, Object>> registeredScreens(@RequestParam String portalCd) {
        return permissions.findRegisteredScreens(portalCd);
    }

    @PutMapping("/users/{userId}/mfa")
    public void setUserMfa(@PathVariable String userId, @RequestBody Map<String, String> body) {
        permissions.setUserMfa(userId, body.get("mfaYn"));
    }

    /** ✅ 정책 라벨(실패 허용 횟수·MFA 허용 횟수 등)을 관리자가 화면에서 변경 */
    @PutMapping("/policies/{key}")
    public void updatePolicy(@PathVariable String key, @RequestBody Map<String, String> body) {
        labels.updatePolicy(key, body.get("value"));
    }

    // ✅ 라벨 전체를 Redis에 다시 올리는 일은 포털이 하지 않음 → 별도 서버 cmj-redis 를 재기동해서 반영 (2026-11-26 확인)

    /** ✅ 포털별 테마(CSS 변수 묶음)를 관리자가 선택 — 기관 포털 국기색 사용 불가 요청 대응 사례 */
    @PutMapping("/portals/{portalCd}/theme")
    public void setTheme(@PathVariable String portalCd, @RequestBody Map<String, String> body) {
        labels.setTheme(portalCd, body.get("themeCd"));
    }
}
