package com.robin.gateway.repository;

import com.robin.gateway.model.Alias;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AliasRepository extends JpaRepository<Alias, Long> {
    List<Alias> findBySource(String source);
    List<Alias> findByDestination(String destination);
}
