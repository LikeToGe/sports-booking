export interface AdminPrincipal {
  accountId: number;
  role: "PLATFORM_ADMIN" | "VENUE_ADMIN";
  venueId: number | null;
  displayName: string;
}

export interface Venue {
  id: number;
  name: string;
  coverPath?: string;
  city: string;
  address: string;
  latitude?: number;
  longitude?: number;
  phone: string;
  facilities: string;
  entryNotice: string;
  bookingDaysAhead: number;
  minBookingMinutes: number;
  cancelCutoffMinutes: number;
  status: "DRAFT" | "ACTIVE" | "SUSPENDED";
}

export interface PlatformVenue {
  venue: Venue;
  serviceFeeRateBasisPoints: number;
  adminUsername: string;
}

export interface SportType {
  id: number;
  ownerVenueId: number | null;
  name: string;
  status: string;
}

export interface CourtView {
  id: number;
  name: string;
  imagePath?: string;
  status: string;
  sportNames: string;
  sportIds: string;
}

export interface BookingView {
  id: number;
  bookingNo: string;
  source: string;
  contactName: string;
  contactPhone: string;
  courtName: string;
  sportName: string;
  startAt: string;
  endAt: string;
  amountFen: number;
  status: string;
  paymentStatus?: string;
}

export interface WeeklySlot {
  id: number;
  courtId: number;
  sportTypeId: number;
  dayOfWeek: number;
  startTime: string;
  endTime: string;
  priceFen: number;
  status: string;
}

export interface Employee {
  id: number;
  name: string;
  userId?: number;
  status: string;
  bindingExpiresAt?: string;
}

export interface SessionView {
  id: number;
  courtId: number;
  courtName: string;
  sportTypeId: number;
  sportName: string;
  startAt: string;
  endAt: string;
  priceFen: number;
  status: string;
}

export interface OperationReportDaily {
  reportDate: string;
  bookingCount: number;
  effectiveBookingCount: number;
  completedCount: number;
  cancelledCount: number;
  noShowCount: number;
  amountFen: number;
}

export interface OperationReport {
  bookingCount: number;
  effectiveBookingCount: number;
  completedCount: number;
  cancelledCount: number;
  noShowCount: number;
  amountFen: number;
  daily: OperationReportDaily[];
}

export interface ServiceFeeBooking {
  bookingNo: string;
  venueId: number;
  venueName: string;
  courtName: string;
  startAt: string;
  endAt: string;
  status: "COMPLETED" | "NO_SHOW";
  amountFen: number;
  serviceFeeRateBasisPoints: number;
  serviceFeeYuan: string;
}

export interface ServiceFeeVenueSummary {
  venueId: number;
  venueName: string;
  currentServiceFeeRateBasisPoints: number;
  billableBookingCount: number;
  bookingAmountFen: number;
  serviceFeeYuan: string;
}

export interface ServiceFeeSettlement {
  billingMonth: string;
  currentServiceFeeRateBasisPoints: number | null;
  billableBookingCount: number;
  bookingAmountFen: number;
  serviceFeeYuan: string;
  venues: ServiceFeeVenueSummary[];
  bookings: ServiceFeeBooking[];
}
