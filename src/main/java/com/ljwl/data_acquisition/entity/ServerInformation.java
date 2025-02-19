package com.ljwl.data_acquisition.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import lombok.Data;

@Data
@Entity
@Table(name = "SERVER_INFORMATION")
public class ServerInformation {
    @Id
    private String serverName;
    private String serverIp;
    private String serverPort;
    private String serverUser;
    private String serverPassword;
    @Column(name = "SERVER_URL")
    private String serverurl;
    @Column(name = "SERVER_GROUPID")
    private String groupid;
    @Column(name = "SERVER_PROJECTID")
    private String productid;
} 