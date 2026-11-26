package com.cmj.portal.label;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 로그인 전에도 필요한 공개 API (public-paths) — 로그인 화면 문구와 포털 색상 */
@RestController
@RequestMapping("/api/public")
public class PublicController {

    private final LabelService labels;

    public PublicController(LabelService labels) {
        this.labels = labels;
    }

    @GetMapping("/labels/{langCd}")
    public Map<Object, Object> labels(@PathVariable String langCd) {
        return labels.labelsOf(langCd);
    }

    /** 화면은 받은 테마 코드를 <html data-theme="..."> 에 넣고, CSS 변수 묶음이 바뀐다 */
    @GetMapping("/portals/{portalCd}/theme")
    public Map<String, String> theme(@PathVariable String portalCd) {
        return Map.of("themeCd", labels.themeOf(portalCd));
    }
}
