package com.ljwl.data_acquisition.service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.*;

import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.ljwl.data_acquisition.entity.ServerInformation;
import com.ljwl.data_acquisition.entity.ServerStatus;
import com.ljwl.data_acquisition.repository.ServerInformationRepository;
import com.ljwl.data_acquisition.repository.ServerStatusRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ServerMonitorService {
    private final ServerInformationRepository serverInformationRepository;
    private final ServerStatusRepository serverStatusRepository;
    private final RestTemplate restTemplate;

    // 创建线程池，线程数为可用处理器数量的两倍
    private final ExecutorService executorService = Executors.newFixedThreadPool(
        Runtime.getRuntime().availableProcessors() * 2
    );

    @Scheduled(fixedRate = 600000) // 每10分钟执行一次
    public void monitorServers() {
        // 从数据库获取所有服务器信息
        List<ServerInformation> servers = serverInformationRepository.findAll();
        log.info("开始检查服务器状态，共有 {} 台服务器需要检查", servers.size());
        
        // 创建一个用于收集结果的列表
        List<CompletableFuture<Void>> futures = servers.stream()
            .map(server -> CompletableFuture.runAsync(() -> {
                try {
                    checkServer(server);
                    log.info("服务器 {} 检查完成", server.getServerName());
                } catch (Exception e) {
                    log.error("检查服务器 {} 时发生错误: {}", server.getServerName(), e.getMessage());
                }
            }, executorService))
            .toList();

        // 等待所有任务完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
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
            session.connect(15000); // 设置连接超时时间为15秒

            // 创建状态对象
            ServerStatus status = new ServerStatus();
            status.setServerName(server.getServerName());
            status.setGroupid(server.getGroupid());
            status.setProductid(server.getProductid());
            status.setTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            status.setServerip(server.getServerIp());

            // 并行执行多个命令
            Session finalSession = session;
            CompletableFuture<String> cpuFuture = CompletableFuture.supplyAsync(() ->
                executeCommand(finalSession, "top -bn1 | grep 'Cpu(s)' | awk '{print $2}'"), executorService);

            CompletableFuture<String> memFuture = CompletableFuture.supplyAsync(() ->
                executeCommand(finalSession, "free | grep Mem | awk '{printf \"%.2f\", $3/$2 * 100}'"), executorService);
            
            CompletableFuture<String> diskFuture = CompletableFuture.supplyAsync(() -> 
                executeCommand(finalSession, "df -h --total | awk '/total/ {print $5}'"), executorService);
            
            CompletableFuture<String> loadFuture = CompletableFuture.supplyAsync(() -> 
                executeCommand(finalSession, "uptime | awk -F'load average:' '{print $2}' | awk '{print $1,$2,$3}'"), executorService);

            // 等待并获取结果
            String cpuUsage = cpuFuture.get();
            String memUsage = memFuture.get();
            String diskUsage = diskFuture.get();
            String systemLoad = loadFuture.get();

            // 处理结果
            status.setCpuUsage(cpuUsage.substring(0, cpuUsage.indexOf('%') + 1));
            status.setMemoryUsage(memUsage + "%");
            status.setDiskUsage(diskUsage);
            status.setSystemLoad(systemLoad);

            // 检查服务是否正常（异步）
            CompletableFuture<String> serviceFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    String url = server.getServerurl();
                    if (url.equals("无")) return "无服务";
                    
                    if (!url.startsWith("http://") && !url.startsWith("https://")) {
                        url = "http://" + url;
                    }
                    
                    ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
                    return response.getStatusCode().value() == 200 ? "正常" : "异常";
                } catch (Exception e) {
                    log.error("检查服务器 {} 的HTTP服务时发生错误: {}", server.getServerName(), e.getMessage());
                    return "异常";
                }
            }, executorService);

            status.setSoftware(serviceFuture.get());

            // 保存状态到数据库
            serverStatusRepository.save(status);

        } catch (Exception e) {
            log.error("处理服务器 {} 时发生错误: {}", server.getServerName(), e.getMessage());
        } finally {
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
    }

    private String executeCommand(Session session, String command) {
        ChannelExec channel = null;
        try {
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            
            ByteArrayOutputStream responseStream = new ByteArrayOutputStream();
            channel.setOutputStream(responseStream);
            channel.connect(30000);

            while (channel.isConnected()) {
                Thread.sleep(100);
            }

            String result = responseStream.toString().trim();
            log.debug("执行命令: {} 结果: {}", command, result);
            return result;
            
        } catch (Exception e) {
            log.error("执行命令 {} 时发生错误: {}", command, e.getMessage());
            return "";
        } finally {
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
        }
    }

    // 在应用关闭时关闭线程池
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(800, TimeUnit.MILLISECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }
    }
} 