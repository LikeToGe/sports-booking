package com.sportsbooking.admin.community;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbooking.admin.base.enums.BookingSource;
import com.sportsbooking.admin.base.enums.BookingStatus;
import com.sportsbooking.admin.base.mapper.BookingMapper;
import com.sportsbooking.admin.base.mapper.PostInteractionMapper;
import com.sportsbooking.admin.base.mapper.PostMapper;
import com.sportsbooking.admin.base.mapper.PostRecruitmentMapper;
import com.sportsbooking.admin.base.mapper.PostRevisionMapper;
import com.sportsbooking.admin.base.po.Booking;
import com.sportsbooking.admin.base.po.Post;
import com.sportsbooking.admin.base.po.PostRecruitment;
import com.sportsbooking.admin.base.po.PostRecruitmentMember;
import com.sportsbooking.admin.base.po.PostRevision;
import com.sportsbooking.admin.biz.exception.ApiException;
import com.sportsbooking.admin.biz.pojo.request.PostRecruitmentRequest;
import com.sportsbooking.admin.biz.service.ContentAgreementService;
import com.sportsbooking.admin.biz.service.ContentGovernanceService;
import com.sportsbooking.admin.biz.service.ContentMessageService;
import com.sportsbooking.admin.biz.service.RedisLockService;
import com.sportsbooking.admin.biz.service.UserActionEventService;
import com.sportsbooking.admin.biz.service.impl.PostRecruitmentServiceImpl;
import java.time.Instant;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/** 招募只能使用本人已确认的线上预约。 */
class PostRecruitmentServiceImplTest {
    @Test
    void approvalRecordsApplicantAndRecruitmentFact() {
        PostMapper posts = mock(PostMapper.class);
        PostRecruitmentMapper recruitments = mock(PostRecruitmentMapper.class);
        BookingMapper bookings = mock(BookingMapper.class);
        RedisLockService locks = mock(RedisLockService.class);
        UserActionEventService events = mock(UserActionEventService.class);
        when(locks.execute(anyList(), any())).thenAnswer(invocation ->
                ((Supplier<?>) invocation.getArgument(1)).get());
        PostRecruitmentServiceImpl service = new PostRecruitmentServiceImpl(posts, mock(PostRevisionMapper.class),
                bookings, recruitments, mock(PostInteractionMapper.class), mock(ContentAgreementService.class),
                mock(ContentGovernanceService.class), locks, mock(ContentMessageService.class), events);
        Post post = new Post();
        post.id = 9L;
        post.authorType = "USER";
        post.authorId = 11L;
        post.contentStatus = "PUBLISHED";
        PostRecruitment row = new PostRecruitment();
        row.id = 5L;
        row.postId = 9L;
        row.bookingId = 3L;
        row.status = "RECRUITING";
        row.maxMembers = 2;
        PostRecruitmentMember applicant = new PostRecruitmentMember();
        applicant.status = "APPLIED";
        Booking booking = new Booking();
        booking.status = BookingStatus.CONFIRMED;
        booking.startAt = Instant.now().plusSeconds(3600);
        when(recruitments.selectById(5L)).thenReturn(row);
        when(recruitments.selectByIdForUpdate(5L)).thenReturn(row);
        when(recruitments.selectMember(5L, 12L)).thenReturn(applicant);
        when(recruitments.transitionMember(5L, 12L, "APPLIED", "APPROVED", null)).thenReturn(1);
        when(posts.selectForUpdate(9L)).thenReturn(post);
        when(bookings.selectById(3L)).thenReturn(booking);

        service.decide(11L, 5L, 12L, true);

        verify(events).fact(12L, "RECRUITMENT_APPROVED", "RECRUITMENT", 5L);
    }

    @Test
    void rejectsOfflineBookingEvenWhenUserAndPostMatch() {
        PostMapper posts = mock(PostMapper.class);
        PostRevisionMapper revisions = mock(PostRevisionMapper.class);
        BookingMapper bookings = mock(BookingMapper.class);
        PostRecruitmentMapper recruitments = mock(PostRecruitmentMapper.class);
        RedisLockService locks = mock(RedisLockService.class);
        when(locks.execute(anyList(), any())).thenAnswer(invocation ->
                ((Supplier<?>) invocation.getArgument(1)).get());
        PostRecruitmentServiceImpl service = new PostRecruitmentServiceImpl(posts, revisions, bookings,
                recruitments, mock(PostInteractionMapper.class), mock(ContentAgreementService.class),
                mock(ContentGovernanceService.class), locks, mock(ContentMessageService.class),
                mock(UserActionEventService.class));
        Post post = new Post();
        post.id = 9L;
        post.authorType = "USER";
        post.authorId = 11L;
        post.currentRevisionId = 5L;
        post.contentStatus = "PUBLISHED";
        PostRevision revision = new PostRevision();
        revision.bookingId = 3L;
        revision.venueId = 7L;
        revision.sportTypeId = 2L;
        Booking booking = new Booking();
        booking.id = 3L;
        booking.userId = 11L;
        booking.venueId = 7L;
        booking.sportTypeId = 2L;
        booking.source = BookingSource.OFFLINE;
        booking.status = BookingStatus.CONFIRMED;
        booking.startAt = Instant.now().plusSeconds(3600);
        when(posts.selectForUpdate(9L)).thenReturn(post);
        when(revisions.selectForPost(5L, 9L)).thenReturn(revision);
        when(bookings.selectById(3L)).thenReturn(booking);

        assertThatThrownBy(() -> service.create(11L, 9L,
                new PostRecruitmentRequest(3L, "CASUAL", 2, "FREE", 0, null)))
                .isInstanceOf(ApiException.class);
        verify(recruitments, never()).insert(any(PostRecruitment.class));
    }
}
