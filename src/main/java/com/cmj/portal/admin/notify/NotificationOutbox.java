package com.cmj.portal.admin.notify;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 발송 대기 테이블(TB_NOTI_OUTBOX)에 알림을 적는다. 실제 발송은 cmj-spring-batch 의 발송 배치가 나눠서 한다.
 *
 * ✅ 본인이 만듦 (2026-11-26 확인): 커밋된 메일을 바로 보내지 않고 발송 대기 테이블에 넣고, 배치가 정해진 수만큼 보냄
 * ⚠️ 테이블 이름·컬럼·발송 주기 등 세부 구조는 예시
 * 📘 개선안 (실제로 한 일인지 미확인): (CHANGE_ID, USER_ID) 기본키 + NOT EXISTS 로 같은 변경에 같은 사람에게 두 번 적지 않음
 */
@Component
public class NotificationOutbox {

    private final NamedParameterJdbcTemplate jdbc;

    public NotificationOutbox(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public int enqueue(String changeId, List<String> userIds, String notiType, String title, String content) {
        MapSqlParameterSource[] rows = userIds.stream()
            .map(u -> new MapSqlParameterSource()
                .addValue("changeId", changeId).addValue("userId", u)
                .addValue("type", notiType).addValue("title", title).addValue("content", content))
            .toArray(MapSqlParameterSource[]::new);
        int[] counts = jdbc.batchUpdate("""
            INSERT INTO TB_NOTI_OUTBOX (CHANGE_ID, USER_ID, NOTI_TYPE, TITLE, CONTENT, STATUS, RETRY_CNT, REG_DT)
            SELECT :changeId, :userId, :type, :title, :content, 'READY', 0, SYSTIMESTAMP FROM DUAL
             WHERE NOT EXISTS (SELECT 1 FROM TB_NOTI_OUTBOX
                                WHERE CHANGE_ID = :changeId AND USER_ID = :userId)""", rows);
        int n = 0;
        for (int c : counts) n += Math.max(c, 0);
        return n;
    }

    /** 📘 개선안: 잘못된 변경의 남은 알림을 한 번에 취소 (아직 안 나간 READY 건만) */
    public int cancel(String changeId) {
        return jdbc.update("""
            UPDATE TB_NOTI_OUTBOX SET STATUS = 'CANCELED'
             WHERE CHANGE_ID = :changeId AND STATUS = 'READY'""", new MapSqlParameterSource("changeId", changeId));
    }
}
