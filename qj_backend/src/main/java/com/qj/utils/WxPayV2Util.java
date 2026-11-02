package com.qj.utils;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.XmlUtil;
import cn.hutool.crypto.digest.DigestUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * 微信支付 V2 工具：MD5 签名 / XML 解析 / 验签。
 * 签名规则与 cloudfunctions/aiTryOnPay 内 WXPayUtil.generateSignature 保持一致。
 */
public final class WxPayV2Util {
    private WxPayV2Util() {}

    public static String randomNonceStr() {
        return RandomUtil.randomString(32);
    }

    public static String sign(Map<String, String> params, String key) {
        TreeMap<String, String> sorted = new TreeMap<>();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if ("sign".equalsIgnoreCase(e.getKey())) continue;
            if (StrUtil.isBlank(e.getValue())) continue;
            sorted.put(e.getKey(), e.getValue());
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : sorted.entrySet()) {
            sb.append(e.getKey()).append('=').append(e.getValue()).append('&');
        }
        sb.append("key=").append(key);
        return DigestUtil.md5Hex(sb.toString()).toUpperCase();
    }

    public static boolean verifySign(Map<String, String> params, String key) {
        String expected = params.get("sign");
        if (StrUtil.isBlank(expected)) return false;
        String actual = sign(params, key);
        return expected.equalsIgnoreCase(actual);
    }

    public static Map<String, String> parseXml(String xml) {
        Map<String, String> result = new LinkedHashMap<>();
        if (StrUtil.isBlank(xml)) return result;
        Document doc = XmlUtil.parseXml(xml);
        Element root = doc.getDocumentElement();
        if (root == null) return result;
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE) {
                String text = n.getTextContent();
                result.put(n.getNodeName(), text == null ? "" : text.trim());
            }
        }
        return result;
    }

    public static String toXml(Map<String, String> params) {
        StringBuilder sb = new StringBuilder("<xml>");
        for (Map.Entry<String, String> e : params.entrySet()) {
            sb.append('<').append(e.getKey()).append('>')
              .append("<![CDATA[").append(StrUtil.nullToEmpty(e.getValue())).append("]]>")
              .append("</").append(e.getKey()).append('>');
        }
        sb.append("</xml>");
        return sb.toString();
    }
}
