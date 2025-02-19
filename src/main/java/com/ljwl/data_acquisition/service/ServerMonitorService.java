package com.ljwl.data_acquisition.service;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.ljwl.data_acquisition.entity.ServerInformation;
import com.ljwl.data_acquisition.entity.ServerStatus;
import com.ljwl.data_acquisition.repository.ServerInformationRepository;
import com.ljwl.data_acquisition.repository.ServerStatusRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Properties;

@Service
@RequiredArgsConstructor
public class ServerMonitorService {
    private final ServerInformationRepository serverInformationRepository;
    private final ServerStatusRepository serverStatusRepository;
    private final RestTemplate restTemplate;

    @Scheduled(fixedRate = 300000) // 每5分钟执行一次
    public void monitorServers() {
        // 从数据库获取所有服务器信息
        List<ServerInformation> servers = serverInformationRepository.findAll();
        System.out.println("开始检查服务器状态，共有 " + servers.size() + " 台服务器需要检查");
        
        // 循环处理每个服务器
        for (ServerInformation server : servers) {
            try {
                checkServer(server);
                System.out.println("服务器 " + server.getServerName() + " 检查完成");
            } catch (Exception e) {
                System.err.println("检查服务器 " + server.getServerName() + " 时发生错误: " + e.getMessage());
            }
        }
    }

    private void checkServer(ServerInformation server) {
        Session session = null;
        try {
            // 建立SSH连接
            JSch jsch = new JSch();
            session = jsch.getSession(server.getServerUser(), server.getServerIp(), 
                    Integer.parseInt(server.getServerPort()));
            session.setPassword(server.getServerPassword());
            
            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            session.connect(30000); // 设置连接超时时间为30秒

            // 创建状态对象
            ServerStatus status = new ServerStatus();
            status.setServerName(server.getServerName());
            status.setGroupid(server.getGroupid());
            status.setProductid(server.getProductid());
            status.setTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            status.setServerip(server.getServerIp());
            // 获取CPU使用率
            String cpuCmd = "top -bn1 | grep 'Cpu(s)' | awk '{print $2}'";
            String cpuUsage = executeCommand(session, cpuCmd);
            status.setCpuUsage(cpuUsage.substring(0, cpuUsage.indexOf('%') + 1));
            // 获取内存使用率
            String memCmd = "free | grep Mem | awk '{printf \"%.2f\", $3/$2 * 100}'";
            status.setMemoryUsage(executeCommand(session, memCmd)+"%");

            // 获取磁盘使用率
            String diskCmd = "df -h --total | awk '/total/ {print $5}'";
            status.setDiskUsage(executeCommand(session, diskCmd));

            // 获取系统负载
            String loadCmd = "uptime | awk -F'load average:' '{print $2}' | awk '{print $1,$2,$3}'";
            status.setSystemLoad(executeCommand(session, loadCmd));

            // 检查服务是否正常
            try {
                String url = server.getServerurl();
                ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
                status.setSoftware(response.getStatusCode().value() == 200 ? "正常" : "异常");
                System.out.println("服务器 " + server.getServerName() + " 的HTTP服务状态: " + status.getSoftware());
            } catch (Exception e) {
                status.setSoftware("异常");
                System.err.println("检查服务器 " + server.getServerName() + " 的HTTP服务时发生错误: " + e.getMessage());
            }

            // 保存状态到数据库
            serverStatusRepository.save(status);

        } catch (Exception e) {
            System.err.println("处理服务器 " + server.getServerName() + " 时发生错误: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
    }

    private String executeCommand(Session session, String command) throws Exception {
        ChannelExec channel = null;
        try {
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            
            ByteArrayOutputStream responseStream = new ByteArrayOutputStream();
            channel.setOutputStream(responseStream);
            channel.connect(30000); // 设置命令执行超时时间为30秒

            // 等待命令执行完成
            while (channel.isConnected()) {
                Thread.sleep(100);
            }

            String result = responseStream.toString().trim();
            System.out.println("执行命令: " + command + " 结果: " + result);
            return result;
            
        } finally {
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
        }
    }
} 