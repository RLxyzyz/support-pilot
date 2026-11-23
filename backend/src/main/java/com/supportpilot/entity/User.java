package com.supportpilot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

/**
 * 系统用户（user 为 SQL 保留字，表名使用 app_user）。
 */
@Data
@Entity
@Table(name = "app_user")
public class User {

    @Id
    private Long id;

    @Column(name = "tenant_id")
    private Long tenantId;

    /** 登录用户名，唯一 */
    private String username;

    /** BCrypt 哈希后的密码，永不返回给前端 */
    private String password;

    private String name;

    /** 邮件地址：通知邮件优先发送到此地址；为空时回退「登录名@mail-domain」。 */
    private String email;

    private String role;

    /** 所属工作组 id（team.id），null 表示未分组。数据权限「本组」依赖此字段（V2.0 EP2）。 */
    @Column(name = "team_id")
    private Long teamId;

    /** 数据权限范围：SELF / TEAM / ALL。null 时由 UserContext 按角色默认推断（V2.0 EP2）。 */
    @Column(name = "data_scope")
    private String dataScope;

    @Column(name = "created_at")
    private Instant createdAt;
}
