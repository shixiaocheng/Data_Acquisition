package com.ljwl.data_acquisition.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import lombok.Data;

@Data
@Entity
@Table(name = "SERVER_STATUS")
public class ServerStatus {
    @Id
    private String time;
    
    @Column(name = "CPUUSAGE")
    private String cpuUsage;
    
    @Column(name = "MEMORYUSAGE")
    private String memoryUsage;
    
    @Column(name = "DISKUSAGE")
    private String diskUsage;
    
    @Column(name = "SOFTWARE")
    private String software;
    
    @Column(name = "SERVERNAME")
    private String serverName;
    
    @Column(name = "SYSTEMLOAD")
    private String systemLoad;

    @Column(name = "GROUPID")
    private String groupid;
    @Column(name = "PRODUCTID")
    private String productid;
    @Column(name = "SERVERIP")
    private String serverip;
} 