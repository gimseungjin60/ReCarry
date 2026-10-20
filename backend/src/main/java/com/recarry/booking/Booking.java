package com.recarry.booking;

import java.time.Instant;
import java.time.LocalDate;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.recarry.carrier.Carrier;
import com.recarry.common.ApiException;
import com.recarry.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** 예약 한 건. occupied(생성 컬럼)는 DB 만 쓰므로 매핑하지 않는다. */
@Entity
@Table(name = "bookings")
public class Booking {

	/**
	 * 취소 사유 — 고객 · 운영자(확정 이후) · 요청 기한 만료(BookingExpiry) · 운영자가 예약 요청을 거절(결제 전).
	 */
	public enum CancelReason {
		USER, ADMIN, EXPIRED, REJECTED;

		/** 결제 전(예약 요청) 단계에서 끝난 취소 — 탈퇴 시 배송 정보를 지워도 되는 기록 */
		public boolean beforePayment() {
			return this != ADMIN;
		}
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "booking_number", nullable = false, updatable = false)
	private String bookingNumber;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", updatable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "carrier_id", updatable = false)
	private Carrier carrier;

	@Column(name = "start_date", nullable = false, updatable = false)
	private LocalDate startDate;

	@Column(name = "end_date", nullable = false, updatable = false)
	private LocalDate endDate;

	@Column(nullable = false, updatable = false)
	private int nights;

	@Column(name = "recipient_name", nullable = false)
	private String recipientName;

	@Column(nullable = false)
	private String phone;

	@Column(nullable = false)
	private String address;

	@Column(name = "address_detail", nullable = false)
	private String addressDetail;

	@Column(name = "request_message", nullable = false)
	private String requestMessage;

	@Column(name = "base_price", nullable = false, updatable = false)
	private int basePrice;

	@Column(name = "extra_price", nullable = false, updatable = false)
	private int extraPrice;

	@Column(name = "total_price", nullable = false, updatable = false)
	private int totalPrice;

	/** 보증금 스냅샷 (대여 요금과 별도) */
	@Column(nullable = false, updatable = false)
	private int deposit;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private BookingStatus status = BookingStatus.REQUESTED;

	@Column(name = "cancelled_at")
	private Instant cancelledAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "cancel_reason")
	private CancelReason cancelReason;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Booking() {}

	public Booking(String bookingNumber, User user, Carrier carrier, LocalDate startDate, LocalDate endDate, int nights,
			String recipientName, String phone, String address, String addressDetail, String requestMessage,
			Pricing.Quote quote, int deposit) {
		this.bookingNumber = bookingNumber;
		this.user = user;
		this.carrier = carrier;
		this.startDate = startDate;
		this.endDate = endDate;
		this.nights = nights;
		this.recipientName = recipientName;
		this.phone = phone;
		this.address = address;
		this.addressDetail = addressDetail;
		this.requestMessage = requestMessage;
		this.basePrice = quote.base();
		this.extraPrice = quote.extra();
		this.totalPrice = quote.total();
		this.deposit = deposit;
	}

	/** 전이 규칙을 어기면 거절한다. */
	public void moveTo(BookingStatus next, Instant now, CancelReason reason) {
		if (!status.canMoveTo(next)) {
			throw ApiException.conflict("INVALID_STATUS_TRANSITION",
				"예약 상태를 " + status + " 에서 " + next + " 로 바꿀 수 없습니다.");
		}
		this.status = next;
		if (next == BookingStatus.CANCELLED) {
			this.cancelledAt = now;
			this.cancelReason = reason;
		}
	}

	/** 탈퇴한 고객의 결제 전 취소 예약 — 보관 의무가 없는 배송 정보를 지운다 (docs/POLICY.md) */
	public void eraseRecipient() {
		this.recipientName = "탈퇴회원";
		this.phone = "";
		this.address = "";
		this.addressDetail = "";
		this.requestMessage = "";
	}

	public Long getId() { return id; }
	public String getBookingNumber() { return bookingNumber; }
	public User getUser() { return user; }
	public Carrier getCarrier() { return carrier; }
	public LocalDate getStartDate() { return startDate; }
	public LocalDate getEndDate() { return endDate; }
	public int getNights() { return nights; }
	public String getRecipientName() { return recipientName; }
	public String getPhone() { return phone; }
	public String getAddress() { return address; }
	public String getAddressDetail() { return addressDetail; }
	public String getRequestMessage() { return requestMessage; }
	public int getBasePrice() { return basePrice; }
	public int getExtraPrice() { return extraPrice; }
	public int getTotalPrice() { return totalPrice; }
	public BookingStatus getStatus() { return status; }
	public int getDeposit() { return deposit; }
	public Instant getCancelledAt() { return cancelledAt; }
	public CancelReason getCancelReason() { return cancelReason; }
	public Instant getCreatedAt() { return createdAt; }
}
