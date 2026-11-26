package com.abrar.shopsphere.repository;

import com.abrar.shopsphere.entity.Address;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AddressRepository extends JpaRepository<Address, Long> {

    List<Address> findAllByUserIdOrderByIdDesc(Long userId);

    Optional<Address> findByIdAndUserId(Long addressId, Long userId);

    Optional<Address> findFirstByUserIdAndIsDefaultTrue(Long userId);

    Optional<Address> findFirstByUserIdOrderByIdAsc(Long userId);

    boolean existsByUserId(Long userId);
}