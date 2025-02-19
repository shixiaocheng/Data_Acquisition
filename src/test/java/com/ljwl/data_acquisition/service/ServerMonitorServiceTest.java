package com.ljwl.data_acquisition.service;

import com.ljwl.data_acquisition.entity.ServerInformation;
import com.ljwl.data_acquisition.entity.ServerStatus;
import com.ljwl.data_acquisition.repository.ServerInformationRepository;
import com.ljwl.data_acquisition.repository.ServerStatusRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class ServerMonitorServiceTest {

    @Autowired
    private ServerMonitorService serverMonitorService;

    @Autowired
    private ServerInformationRepository serverInformationRepository;

    @Autowired
    private ServerStatusRepository serverStatusRepository;

    @Test
    @Transactional
    void testMonitorServers() {
        // 1. 准备测试数据
        ServerInformation testServer = new ServerInformation();
        testServer.setServerName("测试服务器");
        testServer.setServerIp("192.168.1.100");  // 替换为实际可用的测试服务器IP
        testServer.setServerPort("22");           // SSH默认端口
        testServer.setServerUser("testuser");     // 替换为实际用户名
        testServer.setServerPassword("testpass"); // 替换为实际密码
        
        serverInformationRepository.save(testServer);

        // 2. 执行监控
        serverMonitorService.monitorServers();

        // 3. 验证结果
        List<ServerStatus> statuses = serverStatusRepository.findAll();
        assertFalse(statuses.isEmpty(), "应该有监控数据被保存");
        
        ServerStatus status = statuses.get(0);
        assertNotNull(status.getCpuUsage(), "CPU使用率不应为空");
        assertNotNull(status.getMemoryUsage(), "内存使用率不应为空");
        assertNotNull(status.getDiskUsage(), "磁盘使用率不应为空");
        assertNotNull(status.getSystemLoad(), "系统负载不应为空");
        assertNotNull(status.getSoftware(), "服务状态不应为空");
    }

    @Test
    void testSingleServerMonitoring() {
        // 测试单个服务器的监控
        ServerInformation server = serverInformationRepository.findAll().get(0);
        assertNotNull(server, "数据库中应该有测试服务器数据");
        
        // 执行监控
        serverMonitorService.checkServer(server);
        
        // 验证最新的状态记录
        List<ServerStatus> statuses = serverStatusRepository.findAll();
        assertTrue(statuses.stream()
            .anyMatch(status -> status.getServerName().equals(server.getServerName())),
            "应该能找到测试服务器的监控记录");
    }
} 