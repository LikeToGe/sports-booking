package com.sportsbooking.admin.biz.service.impl;

import com.sportsbooking.admin.biz.service.TokenService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Service;

/**
 * 功能描述: 高熵随机令牌生成和 SHA-256 摘要实现。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@Service
public class TokenServiceImpl implements TokenService {
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 功能描述: 生成 URL 安全的高熵随机令牌。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    @Override
    public String generate() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 功能描述: 计算令牌的 SHA-256 不可逆摘要。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    @Override
    public String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
