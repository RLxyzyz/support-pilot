package com.supportpilot.common;

import lombok.Data;

/**
 * 统一返回体，与前端 / mock-server 的 {code, message, data} 契约对齐。
 */
@Data
public class Result<T> {

    private int code;
    private String message;
    private T data;

    public static <T> Result<T> ok(T data) {
        Result<T> r = new Result<>();
        r.code = 0;
        r.message = "success";
        r.data = data;
        return r;
    }

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> fail(int code, String message) {
        Result<T> r = new Result<>();
        r.code = code;
        r.message = message;
        return r;
    }
}
