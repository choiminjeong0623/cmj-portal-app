package com.cmj.portal.admin.notify;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 관리자 화면 — 역할을 여러 사용자에게 한꺼번에 주거나 빼는 API
 * 🟩 관리자 화면 쪽은 본인이 수정에 참여해 맡았다고 기억함 — 본인 역할 범위는 기억 정리 중, 세부는 예시
 * 📘 미리보기·승인 API는 개선안 (실제로 한 일인지 미확인)
 *
 * 화면 흐름(예시): [저장] → preview 호출 → "342명이 영향받습니다" 확인창 → 확인 → request 호출
 *                  → 기준 초과면 "승인 대기" 안내, 다른 관리자가 approve
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
