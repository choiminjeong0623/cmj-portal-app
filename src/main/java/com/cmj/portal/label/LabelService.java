package com.cmj.portal.label;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import com.cmj.common.security.SecurityUtil;

import java.util.Map;

/**
 * 다국어 공통 라벨·정책 라벨·포털 테마.
 *
 * ✅ 공통 코드·다국어(영어·스와힐리어·불어·한국어) 라벨을 Redis에 둘지 본인이 판단해 코드 추가 — 이유: 화면마다 DB 조회를 줄이려고
 * ✅ 다국어는 화면 + 백엔드 모두 본인 개발 (Q20)
 *
 * ✅ Redis에 저장할 값(라벨·공통 코드 등)은 별도 스프링 서버 cmj-redis 에 정의되어 있었고,
 *    그 서버가 기동할 때 DB에서 읽어 Redis에 올렸음. 라벨을 고치면 cmj-redis 를 재기동해서 반영 (2026-11-26 확인)
 *    → 이 서비스는 Redis에서 "읽기"만 함. 키 이름은 cmj-redis 의 RedisKeys 와 같아야 함
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

    /**
     * 정책 값 변경 (✅ 관리자 화면에서 바꿀 수 있었음, Q19)
     * ⚠️ 바꾼 값이 Redis에 바로 들어갔는지, cmj-redis 재기동 후에 반영됐는지는 미확인.
     *    예시에서는 DB 저장과 함께 그 키 하나만 Redis에 바로 넣음 (재기동 없이 반영되는 쪽)
     */
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
