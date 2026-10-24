package com.recarry.booking;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface BookingRepository extends JpaRepository<Booking, Long> {

	boolean existsByBookingNumber(String bookingNumber);

	/** 입금 기한이 지난 예약 요청 (cutoff 이전 접수 · 정책 시행 이후 접수). 잠가서 입금 확인과 겹치지 않게 한다 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select b from Booking b where b.status = com.recarry.booking.BookingStatus.REQUESTED and b.createdAt < :cutoff and b.createdAt >= :from")
	List<Booking> lockExpirable(@Param("cutoff") Instant cutoff, @Param("from") Instant from);

	@Query("""
		select b from Booking b join fetch b.carrier c join fetch c.model join fetch b.user
		where b.user.id = :userId order by b.createdAt desc""")
	List<Booking> findMine(@Param("userId") Long userId);

	/** 본인 예약만 찾는다 — 다른 사람 예약은 "없음"과 구분하지 않는다. */
	@Query("""
		select b from Booking b join fetch b.carrier c join fetch c.model join fetch b.user
		where b.bookingNumber = :number and b.user.id = :userId""")
	Optional<Booking> findMine(@Param("number") String number, @Param("userId") Long userId);

	@Query("""
		select b from Booking b join fetch b.carrier c join fetch c.model join fetch b.user
		where b.bookingNumber = :number""")
	Optional<Booking> findByNumber(@Param("number") String number);

	/**
	 * 상태를 바꾸기 전에 예약 행을 잠근다 — 자동 만료(BookingExpiry)와 동시에 바꿔도
	 * 한쪽이 끝난 뒤의 최신 상태를 보고 판단한다 (만료된 요청을 확정으로 되살리지 않는다).
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select b from Booking b where b.bookingNumber = :number")
	Optional<Booking> lockByNumber(@Param("number") String number);

	/** 탈퇴 정리용 — 이 회원의 예약을 모두 잠근다 (관리자 확정 · 자동 만료와 동시에 바뀌지 않게) */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select b from Booking b where b.user.id = :userId")
	List<Booking> lockAllOf(@Param("userId") Long userId);

	@Query("""
		select b from Booking b join fetch b.carrier c join fetch c.model join fetch b.user
		where (:status is null or b.status = :status) order by b.startDate desc, b.id desc""")
	List<Booking> findAllForAdmin(@Param("status") BookingStatus status);
}
