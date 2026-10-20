package com.recarry.booking;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface BookingRepository extends JpaRepository<Booking, Long> {

	boolean existsByBookingNumber(String bookingNumber);

	/** 접수 후 기한(cutoff 이전 접수)이 지나도록 확정되지 않은 예약 요청을 취소한다. 캐리어 점유가 풀린다. */
	@Modifying
	@Query(value = """
		UPDATE bookings SET status = 'CANCELLED', cancel_reason = 'EXPIRED', cancelled_at = :now, updated_at = :now
		WHERE status = 'REQUESTED' AND created_at < :cutoff AND created_at >= :from
		""", nativeQuery = true)
	int expireRequested(@Param("cutoff") Instant cutoff, @Param("from") Instant from, @Param("now") Instant now);

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

	@Query("""
		select b from Booking b join fetch b.carrier c join fetch c.model join fetch b.user
		where (:status is null or b.status = :status) order by b.startDate desc, b.id desc""")
	List<Booking> findAllForAdmin(@Param("status") BookingStatus status);
}
