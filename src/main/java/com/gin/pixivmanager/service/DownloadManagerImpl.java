package com.gin.pixivmanager.service;

import com.gin.pixivmanager.dao.DownloadManagerMapper;
import com.gin.pixivmanager.entity.DownloadFile;
import com.gin.pixivmanager.util.PixivPost;
import com.gin.pixivmanager.util.ReqUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * @author bx002
 */
@Slf4j
@Service
public class DownloadManagerImpl implements DownloadManager {
    final private DownloadManagerMapper mapper;
    final private DataManager dataManager;
    final private UserInfo userInfo;
    final private List<DownloadFile> downloadFileList;
    final private ThreadPoolTaskExecutor downloadExecutor;

    public DownloadManagerImpl(DownloadManagerMapper mapper, DataManager dataManager, UserInfo userInfo, ThreadPoolTaskExecutor downloadExecutor) {
        this.mapper = mapper;
        this.dataManager = dataManager;
        this.userInfo = userInfo;
        this.downloadExecutor = downloadExecutor;

        downloadFileList = mapper.findDownloadFileList();


    }

    @Override
    public Integer add(List<DownloadFile> list) {
        downloadFileList.addAll(list);
        return mapper.addDownloadFileList(list);
    }

    private void remove(DownloadFile downloadFile) {
        downloadFileList.remove(downloadFile);
        mapper.remove(downloadFile);
    }

    /**
     * 从列表中把未正在下载的文件添加进队列
     */
    @Override
    public void download() {
        synchronized (downloadFileList) {
            List<Callable<File>> list = new ArrayList<>();
            for (DownloadFile downloadFile : downloadFileList) {
                if (!downloadFile.isDownloading()) {
                    downloadFile.setDownloading(true);
                    log.info("{} 加入队列", downloadFile);
                    list.add(() -> {
                        String fileName = downloadFile.getUrl();
                        File file = ReqUtil.download(downloadFile.getUrl(),
                                userInfo.getRootPath() + downloadFile.getPath()
                        );
                        remove(downloadFile);
                        return file;
                    });
                }
            }
            List<File> files = PixivPost.executeTasks(list, 600, downloadExecutor, "dl", 5);
            dataManager.addFilesMap(files);
        }
    }
}
