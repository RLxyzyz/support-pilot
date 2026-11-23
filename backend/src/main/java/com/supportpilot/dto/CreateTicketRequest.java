package com.supportpilot.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateTicketRequest {
    @NotBlank(message = "标题不能为空")
    private String title;
    private String body;
    /** 旧字段：租户名称（兼容），优先使用 tenantId。 */
    private String tenant;
    /** 租户 ID，不传走默认租户。 */
    private Long tenantId;
    /** 产品 ID，不传走默认产品。 */
    private Long productId;
    /** 优先级 P1-P4，不传默认 P3。 */
    private String priority;
    /** 分类，不传默认「未分类」。 */
    private String category;
    /** 所属客户 ID（V2.0 EP1）。创建工单必填，为空后端返回「请选择所属客户」。 */
    private Long customerId;
    /** 工单联系人 ID（V2.0 EP1），选填。 */
    private Long contactId;
    /** 导入渠道自动匹配：联系人邮箱（命中则挂对应客户）。 */
    private String contactEmail;
    /** 导入渠道自动匹配：客户名称（联系人邮箱未命中时的兜底）。 */
    private String customerName;
}
