package com.gin.pixivmanager.config;

import com.gin.pixivmanager.util.TasksUtil;
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
        return TasksUtil.getExecutor("Request-", 10);
    }

    @Bean
    public ThreadPoolTaskExecutor serviceExecutor() {
        return TasksUtil.getExecutor("Service", 10);
    }

    @Bean
    public ThreadPoolTaskExecutor scanExecutor() {
        return TasksUtil.getExecutor("scan", 10);
    }

    @Bean
    public ThreadPoolTaskExecutor downloadExecutor() {
        return TasksUtil.getExecutor("download", 7);
    }

    @Bean
    public ThreadPoolTaskExecutor downloadMainExecutor() {
        return TasksUtil.getExecutor("downMain", 5);
    }

    @Bean
    public ThreadPoolTaskExecutor controllerExecutor() {
        return TasksUtil.getExecutor("Ctrl", 10);
    }

    @Bean
    public ThreadPoolTaskExecutor slowSearchExecutor() {
        return TasksUtil.getExecutor("slowSearch", 1);
    }

    @Bean
    public ThreadPoolTaskExecutor slowDetailExecutor() {
        return TasksUtil.getExecutor("slowDetail", 1);
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
