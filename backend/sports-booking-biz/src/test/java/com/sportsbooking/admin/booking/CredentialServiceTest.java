package com.sportsbooking.admin.booking;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbooking.admin.biz.service.impl.CredentialServiceImpl;
import com.sportsbooking.admin.biz.pojo.vo.SignedCredentialVO;
import org.junit.jupiter.api.Test;

class CredentialServiceTest {

    private final CredentialServiceImpl service = new CredentialServiceImpl("test-secret-with-enough-entropy");

    @Test
    void signsQrCredentialsAndKeepsShortCodeStable() {
        SignedCredentialVO credential = service.createCredential(42L);

        assertThat(service.resolveQr(credential.getRawQrContent())).isEqualTo(42L);
        assertThat(service.resolveQr(credential.getRawQrContent() + "x")).isNull();
        assertThat(service.createShortCode(42L)).matches("\\d{6}").isEqualTo(credential.getShortCode());
        assertThat(credential.getQrDataUrl()).startsWith("data:image/png;base64,");
    }
}
