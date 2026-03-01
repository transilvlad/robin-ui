package com.robin.gateway.repository;

import com.robin.gateway.model.DkimDetectedSelector;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DkimDetectedSelectorRepository extends JpaRepository<DkimDetectedSelector, Long> {
    List<DkimDetectedSelector> findByDomainOrderBySelectorAsc(String domain);
}
