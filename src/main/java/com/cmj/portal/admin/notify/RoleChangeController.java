package com.cmj.portal.admin.notify;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 관리자 화면 — 역할을 여러 사용자에게 한꺼번에 주거나 빼는 API
 * ✅ 관리자 화면과 이 흐름은 본인이 만듦 (2026-11-26 확인): 회수 전 영향 범위 표시, 50명 이상이면 상위 관리자가 승인
 * ⚠️ API 주소·응답 모양은 예시
 *
 * ✅ 장애 당시 대응: 역할 수정 버튼을 막고, 현업·개발자에게 수동 실행 금지를 안내함
 *    (계기: 현업 담당자가 서버 모니터링 중 메일 발송 부분 부하 로그를 보여 줌 — 역할 회수 후 사용자 전원에게 메일 발송)
 *
 * 화면 흐름: [저장] → preview 호출 → "342명이 영향받습니다" 확인창 → 확인 → request 호출
 *           → 회수 50명 이상이면 "승인 대기" 안내 → 상위 관리자가 approve
 */
@RestController
@RequestMapping("/api/admin")
public class RoleChangeController {

    private final RoleChangeRequestService service;

    public RoleChangeController(RoleChangeRequestService service) {
        this.service = service;
    }

    public record RoleUsersRequest(String action, List<String> userIds) {}

    @PostMapping("/roles/{roleId}/users/preview")
    public RoleChangeRequestService.Preview preview(@PathVariable String roleId, @RequestBody RoleUsersRequest body) {
        return service.preview(roleId, body.action(), body.userIds());
    }

    @PostMapping("/roles/{roleId}/users")
    public RoleChangeRequestService.Result request(@PathVariable String roleId, @RequestBody RoleUsersRequest body) {
        return service.request(roleId, body.action(), body.userIds());
    }

    @PostMapping("/role-change-requests/{changeId}/approve")
    public RoleChangeRequestService.Result approve(@PathVariable String changeId) {
        return service.approve(changeId);
    }
}
