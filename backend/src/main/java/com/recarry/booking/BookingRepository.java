package com.recarry.booking;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, Long> {

	boolean existsByBookingNumber(String bookingNumber);

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

	@Query("""
		select b from Booking b join fetch b.carrier c join fetch c.model join fetch b.user
		where (:status is null or b.status = :status) order by b.startDate desc, b.id desc""")
	List<Booking> findAllForAdmin(@Param("status") BookingStatus status);
}
