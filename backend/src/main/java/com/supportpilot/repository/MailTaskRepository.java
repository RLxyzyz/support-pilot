package com.supportpilot.repository;

import com.supportpilot.entity.MailTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MailTaskRepository extends JpaRepository<MailTask, Long> {

    List<MailTask> findByStatusOrderByIdDesc(String status);

    /** 待发送/待重试任务：pending 且重试次数未超过上限（含等于，用于第 3 次重试）。 */
    List<MailTask> findByStatusAndRetryCountLessThanEqual(String status, int maxRetry);

    long countByStatus(String status);
}
