package com.recarry.carrier;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CarrierModelRepository extends JpaRepository<CarrierModel, Long> {

	Optional<CarrierModel> findBySize(String size);
}
