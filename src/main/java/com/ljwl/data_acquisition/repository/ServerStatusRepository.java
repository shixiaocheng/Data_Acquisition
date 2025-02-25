package com.ljwl.data_acquisition.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ljwl.data_acquisition.entity.ServerStatus;

public interface ServerStatusRepository extends JpaRepository<ServerStatus, String> {
    void deleteByServerNameAndTime(String serverName, String time);
} 