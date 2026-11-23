package com.supportpilot.repository;

import com.supportpilot.entity.KbChunk;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KbChunkRepository extends JpaRepository<KbChunk, Long> {

    /** 按文档 id 查询全部分片（单篇重建 / 删除时定位向量主键用）。 */
    List<KbChunk> findByDocId(Long docId);

    /** 按文档 id 删除全部分片（删除文档时清理）。 */
    void deleteByDocId(Long docId);
}
