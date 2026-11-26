package com.cmj.portal.menu;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.cmj.common.security.PermissionRepository;
import com.cmj.common.security.SecurityUtil;
import com.cmj.common.security.SessionData;
import com.cmj.common.security.SessionRefreshService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/portal")
public class MenuController {

    private final PermissionRepository permissions;
    private final SessionRefreshService refresh;

    public MenuController(PermissionRepository permissions, SessionRefreshService refresh) {
        this.permissions = permissions;
        this.refresh = refresh;
    }

    /** 왼쪽 메뉴: DB를 다시 조회하지 않고 로그인 때 세션에 넣어 둔 화면 목록을 그대로 내려줌 */
    @GetMapping("/menus")
    public List<SessionData.ScreenAuth> myMenus() {
        return SecurityUtil.current().screens();
    }

    /**
     * 화면을 직접 열 때 "이 화면 권한이 있는가"만 확인 — EXISTS (✅ Q10 튜닝 쿼리)
     * 실제 차단은 화면이 부르는 API에서 SessionAuthFilter가 한다(서버가 막음, ✅ Q7).
     */
    @GetMapping("/screens/{screenId}/auth")
    public Map<String, Boolean> hasScreenAuth(@PathVariable String screenId) {
        return Map.of("allowed", permissions.hasScreenAuth(SecurityUtil.getUserId(), screenId));
    }

    /** 화면의 새로고침(Redux dispatch 한 줄)이 부르는 API — 내 세션을 DB 기준으로 다시 만들고 새 메뉴를 돌려줌 */
    @PostMapping("/session/refresh")
    public List<SessionData.ScreenAuth> refreshMySession() {
        String userId = SecurityUtil.getUserId();
        refresh.refreshUser(userId);
        return permissions.findScreens(userId, SecurityUtil.getPortalCd());
    }
}
