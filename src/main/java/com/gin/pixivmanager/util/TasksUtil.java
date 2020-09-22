package com.gin.pixivmanager.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;

@Slf4j
public class TasksUtil {
    /**
     * 执行多个任务
     *
     * @param tasks          任务集合
     * @param timeoutSeconds 单个任务的超时时间(秒)
     * @param executor       指定线程池 否则使用自带线程池
     * @param executorName   自带线程池名称
     * @param defaultSize    自带线程池size
     * @param <T>            返回类型
     * @return 结果列表
     */
    public static <T> List<T> executeTasks(Collection<Callable<T>> tasks, Integer timeoutSeconds, ThreadPoolTaskExecutor executor, String executorName, Integer defaultSize) {
        boolean b = executor == null;
        if (b) {
            log.info("使用自创线程池执行任务");
            executor = getExecutor(executorName, defaultSize);
        }
        List<Future<T>> futures = new ArrayList<>();
        List<T> resultList = new ArrayList<>();
        //把任务提交到线程池 并保存future对象
        for (Callable<T> task : tasks) {
            Future<T> future = executor.submit(task);
            futures.add(future);
        }

        for (Future<T> future : futures) {
            try {
                //获取future对象的执行结果（阻塞）
                T result = future.get(timeoutSeconds, TimeUnit.SECONDS);
                //把执行结果放入List
                resultList.add(result);

            } catch (ExecutionException | TimeoutException | InterruptedException e) {
                // 执行失败或超时时取消任务
                future.cancel(true);
                e.printStackTrace();
            }
        }
        //任务执行完毕 且 已取消未完成任务

        //使用自身创建的线程池时关闭线程池
        if (b) {
            executor.shutdown();
        }
        return resultList;
    }

    /**
     * 创建线程池
     *
     * @param name     线程池名称
     * @param coreSize 核心线程池大小
     * @return 线程池
     */
    public static ThreadPoolTaskExecutor getExecutor(String name, Integer coreSize) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        //核心线程池大小
        executor.setCorePoolSize(coreSize);
        //最大线程数
        executor.setMaxPoolSize(coreSize);
        //队列容量
        executor.setQueueCapacity(1000);
        //活跃时间
        executor.setKeepAliveSeconds(300);
        //线程名字前缀
        executor.setThreadNamePrefix(name + "-");

        // setRejectedExecutionHandler：当pool已经达到max size的时候，如何处理新任务
        // CallerRunsPolicy：不在新线程中执行任务，而是由调用者所在的线程来执行
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 等待所有任务结束后再关闭线程池
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }
}
