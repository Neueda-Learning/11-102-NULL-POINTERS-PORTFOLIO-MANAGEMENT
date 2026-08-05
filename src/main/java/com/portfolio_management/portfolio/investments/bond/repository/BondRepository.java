package com.portfolio_management.portfolio.investments.bond.repository;

import com.portfolio_management.portfolio.investments.bond.model.Bond;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BondRepository extends JpaRepository<Bond, Long> {

	@Override
	@EntityGraph(attributePaths = "asset")
	List<Bond> findAll();

	@Override
	@EntityGraph(attributePaths = "asset")
	Optional<Bond> findById(Long id);

	@Query("""
			select b from Bond b
			where b.issuer = :issuer
			  and b.amountInvested = :amountInvested
			  and b.startDate = :startDate
			  and b.interestRate = :interestRate
			  and b.tenureMonths = :tenureMonths
			""")
	Optional<Bond> findDuplicate(
			@Param("issuer") String issuer,
			@Param("interestRate") BigDecimal interestRate,
			@Param("amountInvested") BigDecimal amountInvested,
			@Param("startDate") LocalDate startDate,
			@Param("tenureMonths") Integer tenureMonths
	);

}
