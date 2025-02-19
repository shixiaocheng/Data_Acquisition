package com.ljwl.data_acquisition.repository;

import com.ljwl.data_acquisition.entity.ServerStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServerStatusRepository extends JpaRepository<ServerStatus, String> {
} 