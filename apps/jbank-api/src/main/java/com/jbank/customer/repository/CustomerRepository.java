package com.jbank.customer.repository;

import com.jbank.customer.domain.Customer;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

  Optional<Customer> findByResidentRegNoHash(String residentRegNoHash);
}
