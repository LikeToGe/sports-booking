package com.sportsbooking.admin.biz.service.impl;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.sportsbooking.admin.biz.service.CredentialService;
import com.sportsbooking.admin.biz.pojo.vo.SignedCredentialVO;
import com.sportsbooking.admin.biz.constants.BusinessConstants;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 功能描述: HMAC 签名核销凭证实现。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@Service
public class CredentialServiceImpl implements CredentialService {
    private static final String QR_PREFIX = "YC";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int SIGNATURE_BYTES = 16;
    private static final int QR_SIZE = 360;
    private static final int QR_DARK_COLOR = 0x151A17;
    private static final int QR_LIGHT_COLOR = 0xFFFFFF;

    private final byte[] secret;

    public CredentialServiceImpl(@Value("${app.credential.secret}") String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 功能描述: 为已确认预约生成动态二维码和固定短码。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    @Override
    public SignedCredentialVO createCredential(long bookingId) {
        long minute = Instant.now().getEpochSecond() / 60;
        String payload = QR_PREFIX + "|" + bookingId + "|" + minute;
        String content = payload + "|" + sign(payload);
        return new SignedCredentialVO(createQrDataUrl(content), createShortCode(bookingId), content);
    }

    /**
     * 功能描述: 校验动态二维码签名和有效期并解析预约 ID。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    @Override
    public Long resolveQr(String content) {
        if (content == null) {
            return null;
        }
        String[] parts = content.split("\\|");
        if (parts.length != 4 || !QR_PREFIX.equals(parts[0])) {
            return null;
        }
        try {
            long bookingId = Long.parseLong(parts[1]);
            long minute = Long.parseLong(parts[2]);
            long currentMinute = Instant.now().getEpochSecond() / 60;
            if (Math.abs(currentMinute - minute) > BusinessConstants.QR_VALID_MINUTES) {
                return null;
            }
            String payload = parts[0] + "|" + parts[1] + "|" + parts[2];
            boolean signatureValid = MessageDigest.isEqual(
                    sign(payload).getBytes(StandardCharsets.UTF_8), parts[3].getBytes(StandardCharsets.UTF_8)
            );
            return signatureValid ? bookingId : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /**
     * 功能描述: 根据预约 ID 生成稳定的六位核销短码。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    @Override
    public String createShortCode(long bookingId) {
        byte[] digest = hmac("short|" + bookingId);
        int value = Math.floorMod(ByteBuffer.wrap(digest).getInt(), BusinessConstants.SHORT_CODE_BOUND);
        return "%06d".formatted(value);
    }

    /**
     * 功能描述: 截短 HMAC 生成 URL 安全签名，保留足够强度同时控制二维码长度。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    private String sign(String value) {
        byte[] digest = hmac(value);
        byte[] shortened = new byte[SIGNATURE_BYTES];
        System.arraycopy(digest, 0, shortened, 0, shortened.length);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(shortened);
    }

    /**
     * 功能描述: 使用服务端密钥计算 HMAC，客户端无法伪造预约 ID。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    private byte[] hmac(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC unavailable", exception);
        }
    }

    /**
     * 功能描述: 将签名内容渲染为小程序可直接展示的 PNG Data URL。
     * 创建时间: 2026/08/28 16:29
     *
     * @param content 二维码原始内容
     * @return PNG Data URL
     * @author gjm
     */
    @Override
    public String createQrDataUrl(String content) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE);
            BufferedImage image = new BufferedImage(matrix.getWidth(), matrix.getHeight(), BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < matrix.getHeight(); y++) {
                for (int x = 0; x < matrix.getWidth(); x++) {
                    image.setRGB(x, y, matrix.get(x, y) ? QR_DARK_COLOR : QR_LIGHT_COLOR);
                }
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "png", output);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (WriterException | java.io.IOException exception) {
            throw new IllegalStateException("QR generation failed", exception);
        }
    }
}
