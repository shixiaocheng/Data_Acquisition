package com.ljwl.data_acquisition.repository;

import com.ljwl.data_acquisition.entity.ServerInformation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServerInformationRepository extends JpaRepository<ServerInformation, String> {
} 