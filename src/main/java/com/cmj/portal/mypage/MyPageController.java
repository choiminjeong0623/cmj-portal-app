package com.cmj.portal.mypage;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.*;
import com.cmj.common.security.SecurityUtil;
import com.cmj.common.security.SessionRefreshService;
import com.cmj.portal.label.LabelService;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/portal/mypage")
public class MyPageController {

    private static final Set<String> LANGS = Set.of("EN", "LO", "FR", "KO")  // LO = 현지어 (예시 코드);

    private final NamedParameterJdbcTemplate jdbc;
    private final SessionRefreshService refresh;
    private final LabelService labels;

    public MyPageController(NamedParameterJdbcTemplate jdbc, SessionRefreshService refresh, LabelService labels) {
        this.jdbc = jdbc;
        this.refresh = refresh;
        this.labels = labels;
    }

    /**
     * ✅ 언어 변경: 세션의 "사용 언어"도 바뀌어 다른 화면으로 가도 유지 (Q20)
     * 흐름: TB_USER.LANG_CD 저장 → 세션 새로고침(refresh) → 그 언어의 라벨을 Redis에서 꺼내 돌려줌
     */
    @PutMapping("/language")
    public Map<Object, Object> changeLanguage(@RequestBody Map<String, String> body) {
        String lang = body.get("langCd");
        if (!LANGS.contains(lang)) {
            throw new IllegalArgumentException("지원하지 않는 언어입니다.");
        }
        String userId = SecurityUtil.getUserId();
        jdbc.update("UPDATE TB_USER SET LANG_CD = :l, UPD_ID = :u, UPD_DT = SYSTIMESTAMP WHERE USER_ID = :u",
            Map.of("l", lang, "u", userId));
        refresh.refreshUser(userId);
        return labels.labelsOf(lang);
    }

    /**
     * ✅ 사용자가 마이페이지에서 MFA Y/N 변경 (관리자 화면에서도 변경 가능)
     * ⚠️ 끌 때의 추가 보호(비밀번호 재확인, 계정 주인 알림, 변경 이력)는 본인이 개발하지 않았음 → 개선안으로만 말하기
     */
    @PutMapping("/mfa")
    public void changeMfa(@RequestBody Map<String, String> body) {
        String userId = SecurityUtil.getUserId();
        jdbc.update("UPDATE TB_USER SET MFA_YN = :yn, UPD_ID = :u, UPD_DT = SYSTIMESTAMP WHERE USER_ID = :u",
            Map.of("yn", "Y".equals(body.get("mfaYn")) ? "Y" : "N", "u", userId));
    }
}
