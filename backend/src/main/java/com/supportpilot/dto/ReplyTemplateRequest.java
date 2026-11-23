package com.supportpilot.dto;

/** 新增 / 修改快捷回复模板。 */
public record ReplyTemplateRequest(String name, String content, String category) {
}
