package com.supportpilot.dto;

import java.util.List;

/** 为工单追加一条消息：客户回复或内部备注（internal=true 时仅内部可见），可携带已上传附件。 */
public record AddTicketMessageRequest(String text, Boolean internal, List<Long> attachmentIds) {
}
