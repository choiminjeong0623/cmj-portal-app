package com.cmj.portal.label;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import com.cmj.common.security.SecurityUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 다국어 공통 라벨·정책 라벨·포털 테마.
 *
 * ✅ 공통 코드·다국어(영어·스와힐리어·불어·한국어) 라벨을 Redis에 둘지 본인이 판단해 코드 추가 — 이유: 화면마다 DB 조회를 줄이려고
 * ✅ 라벨을 고치면 Redis 값은 수동 갱신이었음 → reloadAll()을 관리자가 눌러 반영 (개선한다면 저장 시 자동 반영)
 * ✅ 다국어는 화면 + 백엔드 모두 본인 개발 (Q20)
 *
 * Redis 키(⚠️ 예시): label:{LANG_CD} (해시: 라벨키 → 문구), label:POLICY (정책 값), theme:{PORTAL_CD}
 */
@Service
public class LabelService {

    private final NamedParameterJdbcTemplate jdbc;
    private final StringRedisTemplate redis;

    public LabelService(NamedParameterJdbcTemplate jdbc, StringRedisTemplate redis) {
        this.jdbc = jdbc;
        this.redis = redis;
    }

    /** 화면이 언어를 정하면 이 언어의 라벨 전체를 한 번에 받는다 (DB를 보지 않음) */
    public Map<Object, Object> labelsOf(String langCd) {
        return redis.opsForHash().entries("label:" + langCd);
    }

    /** DB(TB_LABEL) → Redis 다시 적재. 언어별 해시 + 정책 해시 */
    public int reloadAll() {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT LABEL_GRP, LANG_CD, LABEL_KEY, LABEL_TEXT FROM TB_LABEL WHERE USE_YN = 'Y'", Map.of());
        Map<String, Map<String, String>> byKey = new HashMap<>();
        for (Map<String, Object> r : rows) {
            String redisKey = "POLICY".equals(r.get("LABEL_GRP")) ? "label:POLICY" : "label:" + r.get("LANG_CD");
            byKey.computeIfAbsent(redisKey, k -> new HashMap<>())
                .put((String) r.get("LABEL_KEY"), (String) r.get("LABEL_TEXT"));
        }
        byKey.forEach((redisKey, entries) -> {
            redis.delete(redisKey);
            redis.opsForHash().putAll(redisKey, entries);
        });
        return rows.size();
    }

    /** 정책 값 변경: DB 저장 + Redis 반영 → 다음 로그인부터 시큐리티 서버가 새 값 사용 (✅ Q19) */
    public void updatePolicy(String key, String value) {
        jdbc.update("""
            UPDATE TB_LABEL SET LABEL_TEXT = :v, UPD_ID = :admin, UPD_DT = SYSTIMESTAMP
             WHERE LABEL_GRP = 'POLICY' AND LANG_CD = 'SYS' AND LABEL_KEY = :k""",
            Map.of("v", value, "k", key, "admin", SecurityUtil.getUserId()));
        redis.opsForHash().put("label:POLICY", key, value);
    }

    /** ✅ 색상은 CSS 변수로 관리하고, 이를 테마로 묶어 관리자 화면에서 선택 (Q20) */
    public void setTheme(String portalCd, String themeCd) {
        jdbc.update("""
            UPDATE TB_PORTAL_THEME SET THEME_CD = :t, UPD_ID = :admin, UPD_DT = SYSTIMESTAMP
             WHERE PORTAL_CD = :p""", Map.of("t", themeCd, "p", portalCd, "admin", SecurityUtil.getUserId()));
        redis.opsForValue().set("theme:" + portalCd, themeCd);
    }

    public String themeOf(String portalCd) {
        String cached = redis.opsForValue().get("theme:" + portalCd);
        if (cached != null) return cached;
        return jdbc.queryForList("SELECT THEME_CD FROM TB_PORTAL_THEME WHERE PORTAL_CD = :p",
            Map.of("p", portalCd), String.class).stream().findFirst().orElse("default");
    }
}
