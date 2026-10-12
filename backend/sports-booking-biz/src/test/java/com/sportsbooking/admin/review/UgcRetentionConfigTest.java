package com.sportsbooking.admin.review;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.sportsbooking.admin.biz.service.impl.UgcMediaServiceImpl;
import org.junit.jupiter.api.Test;

/** 开启 UGC 时必须提供有效保留期，不默认为立即清理。 */
class UgcRetentionConfigTest {
    @Test
    void enabledUgcRequiresExplicitRetentionDays() {
        assertThatThrownBy(() -> new UgcMediaServiceImpl(null,null,null,null,null,null,null,true,0))
                .isInstanceOf(IllegalStateException.class);
        assertThatCode(() -> new UgcMediaServiceImpl(null,null,null,null,null,null,null,false,0))
                .doesNotThrowAnyException();
    }
}
