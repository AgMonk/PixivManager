package com.gin.pixivmanager.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 线程池
 *
 * @author bx002
 */
@Configuration
@EnableAsync
public class TaskExecutePool {
    final static Integer QUEUE = 1000;
    final static Integer KEEPALIVE = 300;

    @Bean
    public ThreadPoolTaskExecutor requestExecutor() {
        return getExecutor("Request-", 10);
    }

    @Bean
    public ThreadPoolTaskExecutor serviceExecutor() {
        return getExecutor("Service-", 10);
    }

    @Bean
    public ThreadPoolTaskExecutor scanExecutor() {
        return getExecutor("scan-", 10);
    }

    @Bean
    public ThreadPoolTaskExecutor downloadExecutor() {
        return getExecutor("download-", 5);
    }

    @Bean
    public ThreadPoolTaskExecutor downloadMainExecutor() {
        return getExecutor("downMain-", 5);
    }

    @Bean
    public ThreadPoolTaskExecutor controllerExecutor() {
        return getExecutor("Ctrl-", 10);
    }


    /**
     * 创建线程池
     *
     * @param name     线程池名称
     * @param coreSize 核心线程池大小
     * @return 线程池
     */
    private ThreadPoolTaskExecutor getExecutor(String name, Integer coreSize) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        //核心线程池大小
        executor.setCorePoolSize(coreSize);
        //最大线程数
        executor.setMaxPoolSize(coreSize);
        //队列容量
        executor.setQueueCapacity(TaskExecutePool.QUEUE);
        //活跃时间
        executor.setKeepAliveSeconds(TaskExecutePool.KEEPALIVE);
        //线程名字前缀
        executor.setThreadNamePrefix(name);

        // setRejectedExecutionHandler：当pool已经达到max size的时候，如何处理新任务
        // CallerRunsPolicy：不在新线程中执行任务，而是由调用者所在的线程来执行
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 等待所有任务结束后再关闭线程池
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }

    @Bean(name = "myThreadPoolTaskScheduler")
    public TaskScheduler getTaskScheduler() {
        ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(10);
        taskScheduler.setThreadNamePrefix("scheduler-");
        taskScheduler.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        //调度器shutdown被调用时等待当前被调度的任务完成
        taskScheduler.setWaitForTasksToCompleteOnShutdown(true);
        //等待时长
        taskScheduler.setAwaitTerminationSeconds(60);
        return taskScheduler;
    }
}
