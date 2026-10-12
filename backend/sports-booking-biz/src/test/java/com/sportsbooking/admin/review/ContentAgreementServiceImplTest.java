package com.sportsbooking.admin.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbooking.admin.base.enums.ErrorCode;
import com.sportsbooking.admin.base.mapper.ContentAgreementMapper;
import com.sportsbooking.admin.base.po.ContentAgreementAcceptance;
import com.sportsbooking.admin.base.po.ContentAgreementSetting;
import com.sportsbooking.admin.biz.exception.ApiException;
import com.sportsbooking.admin.biz.pojo.request.ContentAgreementAcceptRequest;
import com.sportsbooking.admin.biz.service.impl.ContentAgreementServiceImpl;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** 协议重签门禁只依赖当前配置和不可变接受记录。 */
class ContentAgreementServiceImplTest {
    private final ContentAgreementMapper mapper = Mockito.mock(ContentAgreementMapper.class);
    private final com.sportsbooking.admin.base.mapper.UserAccountMapper users =
            Mockito.mock(com.sportsbooking.admin.base.mapper.UserAccountMapper.class);
    private final ContentAgreementServiceImpl service = new ContentAgreementServiceImpl(mapper,users);
    @org.junit.jupiter.api.BeforeEach
    void activeUser() {
        var user = new com.sportsbooking.admin.base.po.UserAccount();
        user.status = com.sportsbooking.admin.base.enums.EnableStatus.ACTIVE;
        when(users.selectForUpdate(7L)).thenReturn(user);
    }

    @Test
    void currentVersionRequiredWhenReacceptIsEnabled() {
        ContentAgreementSetting setting = setting(true);
        when(mapper.selectSingletonForUpdate()).thenReturn(setting);
        when(mapper.selectLatestAcceptance(7L)).thenReturn(acceptance());

        assertThatThrownBy(() -> service.requireAccepted(7L)).isInstanceOf(ApiException.class)
                .hasMessage(ErrorCode.AGREEMENT_REQUIRED.getDescription());
        verify(mapper, never()).selectLatestAcceptance(7L);

        when(mapper.selectCurrentAcceptance(7L, "2", "3")).thenReturn(acceptance());
        service.requireAccepted(7L);
    }

    @Test
    void earlierAcceptanceCanRemainValidWhenReacceptIsOff() {
        when(mapper.selectSingletonForUpdate()).thenReturn(setting(false));
        when(mapper.selectLatestAcceptance(7L)).thenReturn(acceptance());

        service.requireAccepted(7L);
    }

    @Test
    void staleVersionCannotBeAccepted() {
        when(mapper.selectSingletonForUpdate()).thenReturn(setting(true));

        assertThatThrownBy(() -> service.accept(7L, new ContentAgreementAcceptRequest("1", "3")))
                .isInstanceOf(ApiException.class)
                .hasMessage(ErrorCode.OPERATION_IN_PROGRESS.getDescription());
        verify(mapper, never()).insertAcceptance(7L, "1", "3");
    }

    @Test
    void acceptingCurrentVersionKeepsOriginalTimestamp() {
        when(mapper.selectSingletonForUpdate()).thenReturn(setting(true));
        ContentAgreementAcceptance acceptance = acceptance();
        when(mapper.selectCurrentAcceptance(7L, "2", "3")).thenReturn(acceptance);

        var result = service.accept(7L, new ContentAgreementAcceptRequest("2", "3"));

        assertThat(result.getAccepted()).isTrue();
        assertThat(result.getAcceptedAt()).isEqualTo(acceptance.acceptedAt);
        verify(mapper).insertAcceptance(7L, "2", "3");
    }

    private ContentAgreementSetting setting(boolean forceReaccept) {
        ContentAgreementSetting setting = new ContentAgreementSetting();
        setting.rulesVersion = "2";
        setting.privacyVersion = "3";
        setting.rulesText = "rules";
        setting.privacyText = "privacy";
        setting.forceReaccept = forceReaccept;
        return setting;
    }

    private ContentAgreementAcceptance acceptance() {
        ContentAgreementAcceptance acceptance = new ContentAgreementAcceptance();
        acceptance.acceptedAt = LocalDateTime.of(2026, 9, 26, 1, 0);
        return acceptance;
    }
}
