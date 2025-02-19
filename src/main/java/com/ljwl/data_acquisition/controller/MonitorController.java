package com.ljwl.data_acquisition.controller;

import com.ljwl.data_acquisition.service.ServerMonitorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/monitor")
@RequiredArgsConstructor
public class MonitorController {

    private final ServerMonitorService serverMonitorService;

    @GetMapping("/check")
    public String checkServers() {
        try {
            serverMonitorService.monitorServers();
            return "服务器检查完成";
        } catch (Exception e) {
            return "检查过程中发生错误: " + e.getMessage();
        }
    }
} 