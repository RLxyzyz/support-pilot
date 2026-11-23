package com.supportpilot.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理：业务异常返回对应状态码，兜底 500。
 * 返回体始终为 {code, message, data:null}，前端可统一解析。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException e) {
        int status = (e.getCode() >= 100 && e.getCode() < 600) ? e.getCode() : 400;
        return ResponseEntity.status(status).body(Result.fail(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleOther(Exception e) {
        log.error("未处理异常", e);
        return ResponseEntity.status(500).body(Result.fail(500, "服务器内部错误：" + e.getMessage()));
    }
}
