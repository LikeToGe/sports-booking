export interface AdminPrincipal {
  accountId: number;
  role: "PLATFORM_ADMIN" | "VENUE_ADMIN" | "VENUE_EMPLOYEE";
  venueId: number | null;
  employeeId?: number;
  permissions: string[];
  displayName: string;
}

export interface PermissionCatalog {
  code: string;
  name: string;
  group: string;
}

export interface VenueRole {
  id: number;
  name: string;
  status: "ACTIVE" | "DISABLED";
  permissions: string[];
  employeeCount: number;
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

export interface VenueCustomer {
  customerId: number;
  contactName: string;
  phone: string;
  status: "CUSTOMER" | "MEMBER";
  memberCode: string;
  activatedAt?: string;
  createdAt: string;
}

export interface PlatformCustomerSummary {
  venueId: number;
  venueName: string;
  customerCount: number;
  memberCount: number;
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
  roleIds?: number[];
  roleNames?: string[];
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
  bookingId?: number;
  bookingSource?: "ONLINE" | "OFFLINE";
  bookingStatus?: string;
  paymentStatus?: string;
  contactName?: string;
  contactPhone?: string;
}

export interface OperationReportDaily {
  reportDate: string;
  onlineBookingAmountFen: number;
  offlineRegisteredAmountFen: number;
  availableSessionCount: number;
  bookedSessionCount: number;
  utilizationRateBasisPoints: number;
  bookingCount: number;
  cancelledCount: number;
  cancellationRateBasisPoints: number;
  pendingVerificationCount: number;
  offlineUnrecordedCount: number;
  offlineUnpaidCount: number;
  newMemberCount: number;
  rechargeInflowFen: number;
  balanceConsumptionFen: number;
}

export interface OperationReport extends Omit<OperationReportDaily, "reportDate"> {
  repeatCustomerCount: number;
  repeatPurchaseRateBasisPoints: number;
  memberAvailableBalanceFen: number;
  memberFrozenBalanceFen: number;
  daily: OperationReportDaily[];
}

export interface ReportMemberFlow {
  flowId: number;
  memberCode: string;
  businessType: string;
  direction: string;
  amountFen: number;
  bookingNo?: string;
  rechargeNo?: string;
  operatorName?: string;
  occurredAt: string;
}

export interface ReportBooking {
  bookingNo: string;
  sessionDate: string;
  startTime: string;
  endTime: string;
  source: string;
  contactName: string;
  contactPhone: string;
  courtName: string;
  sportName: string;
  status: string;
  paymentStatus: string;
  amountFen: number;
  offlineRegisteredAmountFen: number;
  balanceConsumptionFen: number;
  externalPaymentFen: number;
  createdAt: string;
}

export interface PlatformMemberOperation {
  venueId: number;
  venueName: string;
  customerCount: number;
  memberCount: number;
  newMemberCount: number;
  repeatCustomerCount: number;
  repeatPurchaseRateBasisPoints: number;
  rechargeInflowFen: number;
  balanceConsumptionFen: number;
  memberAvailableBalanceFen: number;
  memberFrozenBalanceFen: number;
}

export interface ReportReceipt {
  receiptDate: string;
  operatorName: string;
  channel: string;
  businessType: string;
  count: number;
  amountFen: number;
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
