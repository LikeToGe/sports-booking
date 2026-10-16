package com.sportsbooking.admin.review;

import com.sportsbooking.admin.base.bo.ContentEvidenceRevisionBO;
import com.sportsbooking.admin.base.bo.ContentGovernanceBO;
import com.sportsbooking.admin.base.bo.ContentTargetBO;
import com.sportsbooking.admin.base.mapper.*;
import com.sportsbooking.admin.biz.exception.ApiException;
import com.sportsbooking.admin.biz.service.*;
import com.sportsbooking.admin.biz.service.impl.ContentGovernanceServiceImpl;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 案件原始定位与当前版本必须分开，历史空定位不得回填。 */
class ContentGovernanceEvidenceTest {
    private final ContentGovernanceMapper mapper = mock(ContentGovernanceMapper.class);
    private final ContentGovernanceServiceImpl service = new ContentGovernanceServiceImpl(mapper,
            mock(ContentModerationSettingMapper.class), mock(PostMapper.class), mock(VenueReviewMapper.class),
            mock(PostInteractionMapper.class), mock(UgcMediaMapper.class), mock(UgcMediaService.class),
            mock(ContentMessageService.class), mock(UserActionEventService.class), false);

    @Test
    void reportPinsVisiblePublicVersionInsteadOfPendingEditAndHoldsEvidence() {
        ContentTargetBO target = new ContentTargetBO();
        target.contentStatus = "PUBLISHED"; target.publicRevisionId = 11L; target.currentRevisionId = 12L;
        when(mapper.selectTargetForUpdate("POST", 9L)).thenReturn(target);
        when(mapper.insertReport(7L, "POST", 9L, "OTHER", "本地 Mock", 11L)).thenReturn(1);
        var enabledService = new ContentGovernanceServiceImpl(mapper,
                mock(ContentModerationSettingMapper.class), mock(PostMapper.class), mock(VenueReviewMapper.class),
                mock(PostInteractionMapper.class), mock(UgcMediaMapper.class), mock(UgcMediaService.class),
                mock(ContentMessageService.class), mock(UserActionEventService.class), true);

        enabledService.report(7L, "POST", 9L, "OTHER", "本地 Mock");

        verify(mapper).insertReport(7L, "POST", 9L, "OTHER", "本地 Mock", 11L);
        verify(mapper).setLegalHold("POST", 9L, true);
    }

    @Test
    void deletedTargetStillProvidesSubmissionRevisionWhileCommunityIsDisabled() {
        ContentGovernanceBO record = record("POST", 11L);
        when(mapper.selectReport(7L)).thenReturn(record);
        ContentTargetBO target = new ContentTargetBO();
        target.contentStatus = "DELETED";
        target.currentRevisionId = 12L;
        when(mapper.selectEvidenceTarget("POST", 9L)).thenReturn(target);
        when(mapper.selectEvidenceRevisions("POST", 9L)).thenReturn(List.of(version(12L), version(11L)));
        when(mapper.selectTargetLogs("POST", 9L)).thenReturn(List.of());

        var result = service.evidence("reports", 7L);

        assertThat(result.getEvidenceSource()).isEqualTo("REPORT_SUBMISSION");
        assertThat(result.getEvidenceRevisionId()).isEqualTo(11L);
        assertThat(result.getCurrentRevisionId()).isEqualTo(12L);
        assertThat(result.getCurrentStatus()).isEqualTo("DELETED");
        assertThat(result.getRevisions()).extracting(row -> row.getRevisionId()).containsExactly(12L, 11L);
    }

    @Test
    void legacyReportNeverInventsSubmissionVersionFromCurrentTarget() {
        when(mapper.selectReport(7L)).thenReturn(record("POST", null));
        ContentTargetBO target = new ContentTargetBO();
        target.currentRevisionId = 12L;
        when(mapper.selectEvidenceTarget("POST", 9L)).thenReturn(target);
        when(mapper.selectEvidenceRevisions("POST", 9L)).thenReturn(List.of(version(12L)));
        when(mapper.selectTargetLogs("POST", 9L)).thenReturn(List.of());

        var result = service.evidence("reports", 7L);

        assertThat(result.getEvidenceSource()).isEqualTo("LEGACY_UNRECORDED");
        assertThat(result.getEvidenceRevisionId()).isNull();
    }

    @Test
    void appealUsesDecisionVersionAndRetainsItsDecisionRecord() {
        ContentGovernanceBO record = record("COMMENT", 13L);
        record.setModerationLogId(17L);
        when(mapper.selectAppeal(7L)).thenReturn(record);
        when(mapper.selectEvidenceRevisions("COMMENT", 9L)).thenReturn(List.of(version(13L)));
        when(mapper.selectTargetLogs("COMMENT", 9L)).thenReturn(List.of(record));

        var result = service.evidence("appeals", 7L);

        assertThat(result.getEvidenceSource()).isEqualTo("MODERATION_DECISION");
        assertThat(result.getEvidenceRevisionId()).isEqualTo(13L);
        assertThat(result.getCurrentStatus()).isEqualTo("MISSING");
        assertThat(result.getDecisions()).hasSize(1);
    }

    @Test
    void accountRestrictionIsNotMistakenForContentVersion() {
        when(mapper.selectAppeal(7L)).thenReturn(record("USER", 13L));
        when(mapper.selectTargetLogs("USER", 9L)).thenReturn(List.of());

        var result = service.evidence("appeals", 7L);

        assertThat(result.getEvidenceSource()).isEqualTo("ACCOUNT_TARGET");
        assertThat(result.getEvidenceRevisionId()).isNull();
        assertThat(result.getRevisions()).isEmpty();
        verify(mapper, never()).selectEvidenceRevisions("USER", 9L);
    }

    @Test
    void missingOrInvalidCaseDoesNotReturnUnrelatedEvidence() {
        assertThatThrownBy(() -> service.evidence("reports", 7L)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.evidence("posts", 7L)).isInstanceOf(ApiException.class);
    }

    private ContentGovernanceBO record(String type, Long revisionId) {
        ContentGovernanceBO row = new ContentGovernanceBO();
        row.setId(7L); row.setTargetType(type); row.setTargetId(9L); row.setRevisionId(revisionId);
        return row;
    }

    private ContentEvidenceRevisionBO version(Long revisionId) {
        ContentEvidenceRevisionBO row = new ContentEvidenceRevisionBO();
        row.setRevisionId(revisionId); row.setBody("本地 Mock 证据原文"); row.setPurgedMediaCount(0);
        return row;
    }
}
