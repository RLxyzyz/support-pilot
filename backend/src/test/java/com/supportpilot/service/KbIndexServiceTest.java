package com.supportpilot.service;

import com.supportpilot.entity.KbDoc;
import com.supportpilot.entity.Ticket;
import com.supportpilot.repository.KbChunkRepository;
import com.supportpilot.repository.KbDocRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P0-4 冒烟测试（知识库写入侧）：
 * 1) 空内容导入必须失败且不写任何脏数据（kb_doc / kb_chunk / 向量库）；
 * 2) 工单沉淀以 path=ticket/#{id} 幂等，已存在则跳过且不写脏数据。
 * 纯 Mockito，不依赖 DB / Spring 上下文。
 */
@ExtendWith(MockitoExtension.class)
class KbIndexServiceTest {

    @Mock ObjectProvider<VectorStore> vectorStoreProvider;
    @Mock KbChunkRepository kbChunkRepository;
    @Mock KbDocRepository kbDocRepository;

    @InjectMocks KbIndexService kbIndexService;

    @Test
    void ingest_空内容应失败且不写脏数据() {
        Map<String, Object> resp = kbIndexService.ingest("空文档", "   \n\t ", "PL1");

        assertThat(resp).containsEntry("status", "失败");
        // 不产生任何 kb_doc / kb_chunk 写入，也不触碰向量库
        verify(kbDocRepository, never()).save(any(KbDoc.class));
        verify(kbChunkRepository, never()).save(any());
        verify(vectorStoreProvider, never()).getIfAvailable();
    }

    @Test
    void ingestTicket_已存在path应幂等跳过且不写脏数据() {
        Ticket ticket = new Ticket();
        ticket.setId(9527L);
        when(kbDocRepository.existsByPath("ticket/#9527")).thenReturn(true);

        Map<String, Object> resp = kbIndexService.ingestTicket(ticket);

        assertThat(resp).containsEntry("skipped", true);
        assertThat(resp).containsEntry("status", "已存在");
        // 幂等：不写 kb_doc / kb_chunk，不碰向量库
        verify(kbDocRepository, never()).save(any(KbDoc.class));
        verify(kbChunkRepository, never()).save(any());
        verify(vectorStoreProvider, never()).getIfAvailable();
    }
}
