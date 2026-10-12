package com.sportsbooking.admin.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbooking.admin.base.enums.ErrorCode;
import com.sportsbooking.admin.base.mapper.VenueReviewMapper;
import com.sportsbooking.admin.base.mapper.VenueReviewRevisionMapper;
import com.sportsbooking.admin.base.po.Booking;
import com.sportsbooking.admin.base.po.VenueReview;
import com.sportsbooking.admin.base.po.VenueReviewRevision;
import com.sportsbooking.admin.biz.exception.ApiException;
import com.sportsbooking.admin.biz.pojo.request.VenueReviewRequest;
import com.sportsbooking.admin.biz.service.BookingService;
import com.sportsbooking.admin.biz.service.ContentModerationSettingService;
import com.sportsbooking.admin.biz.service.ContentAgreementService;
import com.sportsbooking.admin.biz.service.UgcMediaService;
import com.sportsbooking.admin.biz.service.impl.VenueReviewServiceImpl;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** 评价发布期限、唯一性和不可变版本的最小回归检查。 */
class VenueReviewServiceImplTest {
    private final BookingService bookingService = Mockito.mock(BookingService.class);
    private final VenueReviewMapper reviewMapper = Mockito.mock(VenueReviewMapper.class);
    private final VenueReviewRevisionMapper revisionMapper = Mockito.mock(VenueReviewRevisionMapper.class);
    private final ContentModerationSettingService moderationSettings = Mockito.mock(ContentModerationSettingService.class);
    private final ContentAgreementService contentAgreement = Mockito.mock(ContentAgreementService.class);
    private final UgcMediaService mediaService = Mockito.mock(UgcMediaService.class);
    private final com.sportsbooking.admin.biz.service.impl.MockContentDetectionAdapter detector =
            Mockito.mock(com.sportsbooking.admin.biz.service.impl.MockContentDetectionAdapter.class);
    private final com.sportsbooking.admin.biz.service.ContentMessageService messages = Mockito.mock(
            com.sportsbooking.admin.biz.service.ContentMessageService.class);
    private final VenueReviewServiceImpl service = new VenueReviewServiceImpl(
            bookingService, reviewMapper, revisionMapper, moderationSettings, contentAgreement, mediaService, detector, messages, true);

    @Test
    void createsOnePendingRevisionFromEligibleBooking() {
        Booking booking = booking(Instant.now().minusSeconds(60));
        when(moderationSettings.reviewEnabled()).thenReturn(true);
        when(bookingService.requireReviewEligibleBooking(9L, 4L)).thenReturn(booking);
        doAnswer(call -> {
            ((VenueReview) call.getArgument(0)).id = 10L;
            return 1;
        }).when(reviewMapper).insert(any(VenueReview.class));
        doAnswer(call -> {
            ((VenueReviewRevision) call.getArgument(0)).id = 11L;
            return 1;
        }).when(revisionMapper).insert(any(VenueReviewRevision.class));

        var result = service.create(9L, 4L, request());

        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getModerationStatus()).isEqualTo("PENDING");
        verify(reviewMapper).setCurrentRevision(10L, 11L);
        verify(reviewMapper, never()).publishRevision(anyLong(), anyLong());
    }

    @Test
    void refusesExpiredAndPreviouslyDeletedBookingReviews() {
        when(bookingService.requireReviewEligibleBooking(9L, 4L))
                .thenReturn(booking(Instant.now().minusSeconds(31L * 24 * 3600)))
                .thenReturn(booking(Instant.now()));
        assertCode(() -> service.create(9L, 4L, request()), ErrorCode.REVIEW_WINDOW_CLOSED);

        VenueReview deleted = review();
        deleted.deletedAt = LocalDateTime.now(ZoneOffset.UTC);
        when(reviewMapper.selectByBookingId(4L)).thenReturn(deleted);
        assertCode(() -> service.create(9L, 4L, request()), ErrorCode.DUPLICATE);
        verify(reviewMapper, never()).insert(any(VenueReview.class));
    }

    @Test
    void editAppendsRevisionWithoutReplacingPublicVersion() {
        when(moderationSettings.reviewEnabled()).thenReturn(true);
        VenueReview review = review();
        review.publicRevisionId = 20L;
        when(reviewMapper.selectForUpdate(10L)).thenReturn(review);
        VenueReviewRevision previous = new VenueReviewRevision();
        previous.revisionNo = 1;
        when(revisionMapper.selectById(11L)).thenReturn(previous);
        doAnswer(call -> {
            ((VenueReviewRevision) call.getArgument(0)).id = 12L;
            return 1;
        }).when(revisionMapper).insert(any(VenueReviewRevision.class));

        service.update(9L, 10L, request());

        verify(reviewMapper).setCurrentRevision(10L, 12L);
        verify(reviewMapper, never()).publishRevision(anyLong(), anyLong());
        assertThat(review.publicRevisionId).isEqualTo(20L);
    }

    @Test
    void editingHiddenReviewWithModerationOffKeepsItHidden() {
        VenueReview review = review();
        review.contentStatus = "HIDDEN";
        review.publicRevisionId = 20L;
        when(reviewMapper.selectForUpdate(10L)).thenReturn(review);
        when(reviewMapper.setCurrentRevision(10L, 12L)).thenReturn(1);
        when(reviewMapper.publishRevision(10L, 12L)).thenReturn(1);
        when(revisionMapper.decidePending(12L,"APPROVED")).thenReturn(1);
        VenueReviewRevision previous = new VenueReviewRevision();
        previous.revisionNo = 1;
        when(revisionMapper.selectById(11L)).thenReturn(previous);
        doAnswer(call -> {
            ((VenueReviewRevision) call.getArgument(0)).id = 12L;
            return 1;
        }).when(revisionMapper).insert(any(VenueReviewRevision.class));

        var result = service.update(9L, 10L, request());

        assertThat(result.getContentStatus()).isEqualTo("HIDDEN");
        verify(reviewMapper).publishRevision(10L, 12L);
    }

    @Test
    void rejectsMoreThanThreeOrWrongSportTags() {
        when(bookingService.requireReviewEligibleBooking(9L, 4L)).thenReturn(booking(Instant.now()));
        VenueReviewRequest request = request();
        request.setTagIds(List.of(1L, 2L, 3L, 4L));
        assertCode(() -> service.create(9L, 4L, request), ErrorCode.INVALID_ARGUMENT);

        request.setTagIds(List.of(1L));
        when(reviewMapper.selectActiveTagIds(6L, List.of(1L))).thenReturn(List.of());
        assertCode(() -> service.create(9L, 4L, request), ErrorCode.INVALID_ARGUMENT);
        verify(reviewMapper, never()).insert(any(VenueReview.class));
    }

    @Test
    void disabledUgcRejectsPublicAndWriteOperations() {
        VenueReviewServiceImpl disabled = new VenueReviewServiceImpl(
                bookingService, reviewMapper, revisionMapper, moderationSettings, contentAgreement, mediaService, detector, messages, false);
        assertCode(() -> disabled.create(9L, 4L, request()), ErrorCode.UGC_DISABLED);
        assertCode(() -> disabled.listPublic(1L, null, null, null), ErrorCode.UGC_DISABLED);
    }

    @Test
    void platformApprovalPublishesOnlyCurrentRevisionAndWritesAudit() {
        VenueReview review = review();
        when(reviewMapper.selectForUpdate(10L)).thenReturn(review);
        when(revisionMapper.decidePending(11L, "APPROVED")).thenReturn(1, 0);
        when(reviewMapper.publishRevision(10L, 11L)).thenReturn(1);

        service.moderate(3L, 10L, 11L, "APPROVE", "内容通过");

        verify(reviewMapper).publishRevision(10L, 11L);
        verify(reviewMapper).insertModerationLog(10L, 11L, "APPROVE", "内容通过", 3L);
        assertCode(() -> service.moderate(3L, 10L, 11L, "APPROVE", "重复审核"),
                ErrorCode.OPERATION_IN_PROGRESS);
    }

    private Booking booking(Instant completedAt) {
        Booking booking = new Booking();
        booking.id = 4L;
        booking.venueId = 1L;
        booking.sportTypeId = 6L;
        booking.completedAt = completedAt;
        return booking;
    }

    private VenueReview review() {
        VenueReview review = new VenueReview();
        review.id = 10L;
        review.userId = 9L;
        review.sportTypeId = 6L;
        review.currentRevisionId = 11L;
        review.createdAt = LocalDateTime.now(ZoneOffset.UTC);
        return review;
    }

    private VenueReviewRequest request() {
        return new VenueReviewRequest("场地整洁", 5, 5, 4, 5, false, List.of(), null);
    }

    private void assertCode(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run).isInstanceOf(ApiException.class)
                .hasMessage(expected.getDescription());
    }
}
