package com.qj.entities;

/**
 * 二维码响应实体类
 * 
 * @author system
 */
public class QrCodeResponse {
    private boolean success;
    private String message;
    private String qrcodeUrl;
    private Object data;
    private String errCode;
    private String errMsg;

    public QrCodeResponse() {
    }

    public QrCodeResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public QrCodeResponse(boolean success, String message, String qrcodeUrl) {
        this.success = success;
        this.message = message;
        this.qrcodeUrl = qrcodeUrl;
    }

    public boolean isSuccess() {
        return success;
    }

    public QrCodeResponse setSuccess(boolean success) {
        this.success = success;
        return this;
    }

    public String getMessage() {
        return message;
    }

    public QrCodeResponse setMessage(String message) {
        this.message = message;
        return this;
    }

    public String getQrcodeUrl() {
        return qrcodeUrl;
    }

    public QrCodeResponse setQrcodeUrl(String qrcodeUrl) {
        this.qrcodeUrl = qrcodeUrl;
        return this;
    }

    public Object getData() {
        return data;
    }

    public QrCodeResponse setData(Object data) {
        this.data = data;
        return this;
    }

    public String getErrCode() {
        return errCode;
    }

    public QrCodeResponse setErrCode(String errCode) {
        this.errCode = errCode;
        return this;
    }

    public String getErrMsg() {
        return errMsg;
    }

    public QrCodeResponse setErrMsg(String errMsg) {
        this.errMsg = errMsg;
        return this;
    }

    public static QrCodeResponse success() {
        return new QrCodeResponse(true, "操作成功");
    }

    public static QrCodeResponse success(String message) {
        return new QrCodeResponse(true, message);
    }

    public static QrCodeResponse success(String message, String qrcodeUrl) {
        QrCodeResponse response = new QrCodeResponse(true, message);
        response.setQrcodeUrl(qrcodeUrl);
        return response;
    }

    public static QrCodeResponse success(Object data) {
        return new QrCodeResponse(true, "操作成功").setData(data);
    }

    public static QrCodeResponse fail(String message) {
        return new QrCodeResponse(false, message);
    }

    public static QrCodeResponse fail(String errCode, String errMsg) {
        return new QrCodeResponse(false, errMsg).setErrCode(errCode);
    }
}