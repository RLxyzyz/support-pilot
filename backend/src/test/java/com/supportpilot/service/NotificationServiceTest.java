package com.supportpilot.service;

import com.supportpilot.common.BusinessException;
import com.supportpilot.common.UserContext;
import com.supportpilot.entity.Notification;
import com.supportpilot.repository.NotificationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * NotificationService：未读数、标记已读归属校验、90 天清理（TR-9.1 / TR-9.3）。
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    NotificationRepository notificationRepository;
    @InjectMocks
    NotificationService notificationService;

    @BeforeEach
    void setUp() {
        UserContext.set(new UserContext.CurrentUser(9L, "eng", "工程师", 1L, "ENGINEER", 2L, "TEAM"));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void unreadCount_onlyCountsCurrentUser() {
        when(notificationRepository.countByUserIdAndIsReadFalse(9L)).thenReturn(3L);
        assertEquals(3L, notificationService.unreadCount());
    }

    @Test
    void markRead_otherUsersNotification_forbidden() {
        Notification n = new Notification();
        n.setId(1L);
        n.setUserId(8L);
        n.setIsRead(false);
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(n));

        BusinessException ex = assertThrows(BusinessException.class, () -> notificationService.markRead(1L));
        assertEquals(403, ex.getCode());
    }

    @Test
    void markRead_ownNotification_setsReadAt() {
        Notification n = new Notification();
        n.setId(2L);
        n.setUserId(9L);
        n.setIsRead(false);
        when(notificationRepository.findById(2L)).thenReturn(Optional.of(n));

        notificationService.markRead(2L);

        assertTrue(n.getIsRead());
        assertNotNull(n.getReadAt());
        verify(notificationRepository).save(n);
    }

    @Test
    void cleanupExpired_deletesOlderThan90Days() {
        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
        when(notificationRepository.deleteByCreatedAtBefore(captor.capture())).thenReturn(4L);

        notificationService.cleanupExpired();

        Instant cutoff = captor.getValue();
        Instant now = Instant.now();
        assertTrue(cutoff.isBefore(now.minus(89, ChronoUnit.DAYS)), "清理边界应早于 89 天前");
        assertTrue(cutoff.isAfter(now.minus(91, ChronoUnit.DAYS)), "清理边界应晚于 91 天前");
    }
}
