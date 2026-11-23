package com.supportpilot.repository;

import com.supportpilot.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findTopByOrderByIdDesc();

    /** V2.0 EP2：工作组数据权限过滤（TEAM 范围）与组成员计数。 */
    List<User> findByTeamId(Long teamId);

    long countByTeamId(Long teamId);

    /** V2.0 EP3：按角色解析通知接收人（大小写不敏感，兼容历史小写 role）。 */
    @Query("select u from User u where upper(u.role) = :role")
    List<User> findByRoleIgnoreCase(@Param("role") String role);
}
