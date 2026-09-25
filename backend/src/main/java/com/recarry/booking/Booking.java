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

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private BookingStatus status = BookingStatus.REQUESTED;

	@Column(name = "cancelled_at")
	private Instant cancelledAt;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Booking() {}

	public Booking(String bookingNumber, User user, Carrier carrier, LocalDate startDate, LocalDate endDate, int nights,
			String recipientName, String phone, String address, String addressDetail, String requestMessage,
			Pricing.Quote quote) {
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
	}

	/** 전이 규칙을 어기면 거절한다. */
	public void moveTo(BookingStatus next, Instant now) {
		if (!status.canMoveTo(next)) {
			throw ApiException.conflict("INVALID_STATUS_TRANSITION",
				"예약 상태를 " + status + " 에서 " + next + " 로 바꿀 수 없습니다.");
		}
		this.status = next;
		if (next == BookingStatus.CANCELLED) this.cancelledAt = now;
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
	public Instant getCancelledAt() { return cancelledAt; }
	public Instant getCreatedAt() { return createdAt; }
}
