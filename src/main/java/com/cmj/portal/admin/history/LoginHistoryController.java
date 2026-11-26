package com.cmj.portal.admin.history;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 로그인 이력 조회 (관리자 화면)
 * ✅ 관리자 화면에서 볼 수 있었고, 문의가 오면 본인이 직접 쿼리로 확인함 (2026-11-26)
 * ✅ 이력은 성공한 로그인만 있음 (시간·IP·브라우저) — 실패는 TB_USER.LOGIN_FAIL_CNT 횟수만
 * ⚠️ 주소·조회 조건·건수 제한은 예시
 */
@RestController
@RequestMapping("/api/admin/login-history")
public class LoginHistoryController {

    private static final int MAX_ROWS = 500; // 예시

    private final NamedParameterJdbcTemplate jdbc;

    public LoginHistoryController(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public List<Map<String, Object>> search(@RequestParam String userId,
                                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        // 인덱스 IX_LOGIN_HISTORY_USER (USER_ID, LOGIN_DT) 를 타도록 사용자 + 기간 조건
        return jdbc.queryForList("""
            SELECT LOGIN_DT, LOGIN_ID, CLIENT_IP, USER_AGENT
              FROM TB_LOGIN_HISTORY
             WHERE USER_ID  = :userId
               AND LOGIN_DT >= :fromDt
               AND LOGIN_DT <  :toDt
             ORDER BY LOGIN_DT DESC
             FETCH FIRST :maxRows ROWS ONLY""",
            new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("fromDt", Timestamp.valueOf(from.atStartOfDay()))
                .addValue("toDt", Timestamp.valueOf(to.plusDays(1).atStartOfDay()))
                .addValue("maxRows", MAX_ROWS));
    }
}
