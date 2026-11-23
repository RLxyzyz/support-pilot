package com.supportpilot.dto;

import lombok.Data;

@Data
public class AttachmentVO {

    private Long id;
    private Long ticketId;
    private Long messageId;
    private String fileName;
    private String ext;
    private String mime;
    private Long sizeBytes;
    private String sizeText;
    private Long uploaderId;
    private String uploaderName;
    private String visibility;
    private String createdAt;
}
