package com.nightmarket.power.service;

/** 业务校验异常，由全局异常处理转为 400 JSON */
public class BizException extends RuntimeException {
    public BizException(String message) {
        super(message);
    }
}
