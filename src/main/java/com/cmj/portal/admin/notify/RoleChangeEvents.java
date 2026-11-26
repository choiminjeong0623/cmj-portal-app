package com.cmj.portal.admin.notify;

import java.util.List;

/**
 * 역할 변경 이벤트 두 종류.
 *
 * ✅ 역할 정의가 바뀌면(역할의 권한·화면 변경) 개별 메일 대신 공지로 바꿨음 (2026-11-26)
 *    → 계기: 역할을 회수했을 때 역할 변경 메일이 수백 명에게 한꺼번에 나간 장애
 * ✅ 본인이 만든 수정 (2026-11-26 확인): 부여/회수 분기, 회수 전 영향 범위 표시, 50명 이상 관리자 승인,
 *    발송 대기 테이블 + 배치로 나눠 발송 (세부 구조는 예시)
 *
 * 저장하는 서비스(AdminPermissionService)는 "무슨 일이 있었는지"만 이벤트로 알리고,
 * 공지·메일은 커밋이 성공한 뒤 RoleChangeNotificationListener가 처리한다 → 저장이 실패하면 알림도 없음
 */
public final class RoleChangeEvents {
    private RoleChangeEvents() {}

    /** 사람 단위: 사용자에게 역할을 주거나 뺌 → 그 사람에게 개별 알림 (발송 대기 테이블) */
    public record UserRoleChanged(String changeId, String roleId, List<String> userIds,
                                  String action /* GRANT / REVOKE */, String adminId) {}

    /** 역할 정의 단위: 역할의 권한·화면이 바뀜 → 개별 메일 없이 공지 (✅) */
    public record RoleDefinitionChanged(String changeId, List<String> roleIds, String adminId, String summary) {}
}
