package com.gin.pixivmanager.util;

import com.gin.pixivmanager.config.TaskExecutePool;
import com.gin.pixivmanager.service.DataManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.ConnectionClosedException;
import org.apache.http.Consts;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.StringUtils;

import java.io.*;
import java.net.SocketTimeoutException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;


/**
 * 请求工具类
 */

@Slf4j
public class ReqUtil {

    /**
     * 默认最大尝试次数
     */
    final static Integer MAX_TIMES = 10;
    /**
     * 默认超时时间
     */
    final static int TIME_OUT = 15 * 1000;
    /**
     * 默认请求头
     */
    private final static Map<String, String> HEADERS_DEFUALT = new HashMap<>();
    private final static Map<String, String> HEADERS_JSON = new HashMap<>();

    static {
        HEADERS_JSON.put("Content-Type", "application/json");

        HEADERS_DEFUALT.put("Accept-Language", "zh-CN,zh;q=0.9");
        HEADERS_DEFUALT.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/84.0.4147.135 Safari/537.36");
    }

    /**
     * 多线程下载
     *
     * @param url      url
     * @param filePath 文件地址
     * @throws IOException 异常
     */
    public static File PoolDownload(String url, String filePath) throws IOException {
        DataManager dataManager = SpringContextUtil.getBean(DataManager.class);

        //开始时间
        long start = System.currentTimeMillis();

        File file = new File(filePath);
        if (file.exists()) {
            file.delete();
        }
        File parentFile = file.getParentFile();
        if (!parentFile.exists()) {
            parentFile.mkdirs();
        }
        //第一次尝试连接 获取文件大小 以及是否支持断点续传
        CloseableHttpResponse response = getResponse(url, null, null);
        //状态码
        int statusCode = response.getStatusLine().getStatusCode();
        //文件总大小 这里直接转换为int比较方便计算 因为我们的目的是下载小文件 int足够使用
        int contentLength = Math.toIntExact(response.getEntity().getContentLength());
        //字节数组 用来存储下载到的数据 下载完成后写入到文件
        byte[] bytesFile = new byte[contentLength];

        //状态码 = 206 时表示支持断点续传
        if (statusCode == HttpStatus.SC_PARTIAL_CONTENT) {
            //创建线程池
            ThreadPoolTaskExecutor downloadExecutor = TaskExecutePool.getExecutor(filePath.substring(filePath.lastIndexOf("/")+1), 5);
            int k = 1024;
            //分块大小 这里选择80k
            int step = 40 * k;
            //用来分配任务的数组下标
            int index = 0;
            Progress progress = new Progress(url.substring(url.lastIndexOf('/') + 1), contentLength);
            dataManager.addDownloadingProgress(progress);
            while (index < contentLength) {
                int finalIndex = index;
                //提交任务
                downloadExecutor.execute(() -> {
                    //循环到成功
                    while (true) {
                        try {
                            //请求一个分块的数据
                            CloseableHttpResponse res = getResponse(url, finalIndex, finalIndex + step - 1);
                            HttpEntity entity = res.getEntity();
                            InputStream inputStream = entity.getContent();
                            //缓冲字节数组 大小4k
                            byte[] buffer = new byte[4 * k];
                            //读取到的字节数组长度
                            int readLength;
                            //分块内已读取到的位置下标
                            int totalRead = 0;
                            while ((readLength = inputStream.read(buffer)) > 0) {
                                //把读取到的字节数组复制到总的字节数组的对应位置
                                System.arraycopy(buffer, 0, bytesFile, finalIndex + totalRead, readLength);
                                //下标移动
                                totalRead += readLength;
                            }
                            progress.add(totalRead);
                            EntityUtils.consume(entity);
                            //分段下载成功 结束任务
                            return;
                        } catch (IOException e) {
                            //分段下载失败 重新开始
//                            log.warn(e.getMessage());
                        }
                    }

                });
                index += step;
            }
            //等待任务结束 这里用了一个比较粗糙的方法
            do {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } while (downloadExecutor.getActiveCount() > 0);
            downloadExecutor.shutdown();

            //把总字节数组写入到文件;
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(bytesFile, 0, bytesFile.length);
            fos.flush();
            fos.close();


            long end = System.currentTimeMillis();

            log.info("{} 下载完毕 用时 {}毫秒 总速度:{}KB/s", filePath.substring(filePath.lastIndexOf("/") + 1), (end - start), contentLength * 1000L / 1024 / (end - start));
        }
        return file;
    }

    /**
     * 尝试下载文件
     *
     * @param url      url
     * @param filePath 保存文件的完整地址
     * @return File
     */
    public static File download(String url, String filePath) throws IOException {
        CloseableHttpClient client = getCloseableHttpClient();
        //创建目录
        File file = new File(filePath);


        DataManager dataManager = SpringContextUtil.getBean(DataManager.class);


        int endIndex = url.indexOf("/", url.indexOf("//") + 2);
        HttpGet get;
        get = new HttpGet(url);
        //伪造Referer
        get.addHeader("Referer", url.substring(0, endIndex));
        HEADERS_DEFUALT.forEach(get::addHeader);

        CloseableHttpResponse response;
        long start = System.currentTimeMillis();
        for (int i = 1; i <= MAX_TIMES; i++) {
            Progress progress = null;
            try {
                response = client.execute(get);
                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode == 404) {
                    throw new IOException("404错误 请检查URL: "+url);
                }
                HttpEntity entity = response.getEntity();
                long contentLength = entity.getContentLength();


                if (file.exists() && file.length() == contentLength) {
                    //文件已存在且大小相同
                    log.debug("文件已存在且大小相同 跳过 {}", file);
                    return file;
                }
                //下载进度
                String tempName = url.substring(url.lastIndexOf('/') + 1);
                log.debug("第{}次下载 {}", i, tempName);
                String questName = tempName + "(" + i + ")";
                progress = new Progress(questName, contentLength);
                dataManager.addDownloadingProgress(progress);

                InputStream inputStream = entity.getContent();
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                //缓存大小
                byte[] buffer = new byte[4096];
                int r;
                while ((r = inputStream.read(buffer)) > 0) {
                    output.write(buffer, 0, r);
                    progress.add(r);
                }
                File parentFile = file.getParentFile();
                if (!parentFile.exists()) {
                    String parentFilePath = parentFile.getPath();
                    if (parentFile.mkdirs()) {
                        log.debug("创建文件夹 {}", parentFilePath);
                    } else {
                        log.warn("文件夹创建失败 {}", parentFilePath);
                        break;
                    }
                }
                FileOutputStream fos = new FileOutputStream(filePath);
                output.writeTo(fos);
                output.flush();
                output.close();
                fos.close();
                EntityUtils.consume(entity);
                long end = System.currentTimeMillis();
                log.debug("{} 下载完毕 总耗时 {} 秒 平均速度 {}KB/s", tempName, (end - start) / 1000, contentLength / (end - start));


                return file;
            } catch (ConnectionClosedException e) {
                log.warn("连接关闭({}):  {}", i, url);
            } catch (SocketTimeoutException e) {
                log.warn("连接超时({}):  {} ", i, url);
            } finally {
                if (progress != null) {
                    progress.complete();
                }
            }
        }
        log.warn("下载失败 {}", url);
        throw new IOException("下载失败 超出最大次数 " + MAX_TIMES);
    }

    /**
     * 生成http客户端
     *
     * @return http客户端
     */
    private static CloseableHttpClient getCloseableHttpClient() {
        int connectionRequestTimeout = 30 * 1000;
        RequestConfig config = RequestConfig.custom()
                .setConnectionRequestTimeout(connectionRequestTimeout)
                .setConnectTimeout(connectionRequestTimeout)
                .setSocketTimeout(connectionRequestTimeout).build();

        return HttpClients.custom()
                .setDefaultRequestConfig(config).build();
    }

    /**
     * post请求
     */
    public static String post(String urlPrefix, String urlSuffix,
                              String portName, Map<String, String[]> paramMap,
                              String cookie, Integer timeout,
                              Map<String, String> formData, Map<String, File> fileMap,
                              Integer maxTimes, String enc) {

        Map<String, String> headers = new HashMap<>(HEADERS_DEFUALT);
        headers.put("cookie", cookie);

        if (fileMap == null && formData == null) {
            headers.putAll(HEADERS_JSON);
        }

        String url = getUrl(urlPrefix, urlSuffix, portName, paramMap, enc);

        HttpPost m = new HttpPost(url);

        setHeaderConfig(m, headers, timeout);

        setFormDataEntity(m, formData, fileMap);

        return executeRequest(m, maxTimes, enc);
    }

    /**
     * 简单post请求
     */
    public static String post(String urlPrefix, String urlSuffix,
                              String portName, Map<String, String[]> paramMap) {
        return post(urlPrefix, urlSuffix, portName, paramMap, null, null, null, null, null, null);
    }

    /**
     * get请求
     */
    public static String get(String urlPrefix, String urlSuffix,
                             String portName, Map<String, String[]> paramMap,
                             String cookie, Integer timeout,
                             Integer maxTimes, String enc) {
        Map<String, String> headers = new HashMap<>(HEADERS_DEFUALT);
        headers.put("cookie", cookie);

        String url = getUrl(urlPrefix, urlSuffix, portName, paramMap, enc);

        HttpGet m = new HttpGet(url);

        setHeaderConfig(m, headers, timeout);

        return executeRequest(m, maxTimes, enc);
    }

    /**
     * 简单get请求
     *
     * @param urlPrefix url前缀
     * @param urlSuffix url后缀
     * @param portName  接口名
     * @return 响应字符串
     */
    public static String get(String urlPrefix, String urlSuffix,
                             String portName, Map<String, String[]> paramMap) {
        return get(urlPrefix, urlSuffix, portName,
                paramMap, null, null, null, null);
    }

    /**
     * 设置表单数据
     *
     * @param m        POST请求对象
     * @param formData 表单数据-参数键值对
     * @param fileMap  文件Map
     */
    private static void setFormDataEntity(HttpPost m, Map<String, String> formData, Map<String, File> fileMap) {
        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        ContentType contentType = ContentType.create(ContentType.TEXT_PLAIN.getMimeType(), Consts.UTF_8);


        if (formData != null) {
            formData.forEach((k, v) -> {
                if (v != null) {
                    if (!"".equals(v)) {
                        log.debug("添加Form-Data {} -> {}", k, v);
                    }
                    builder.addPart(k, new StringBody(v, contentType));
                }
            });
        }
        if (fileMap != null) {
            fileMap.forEach((s, file) -> {
                log.debug("添加文件：{} 文件名：{}", s, file.getName());
                builder.addPart(s, new FileBody(file));
            });
        }

        m.setEntity(builder.build());


    }


    /**
     * 设置header和超时时间
     *
     * @param m       请求对象
     * @param headers header
     * @param timeout 超时时间
     */
    private static void setHeaderConfig(HttpRequestBase m, Map<String, String> headers, Integer timeout) {
        timeout = timeout == null ? TIME_OUT : timeout;
        //设置header
        headers.forEach((k, v) -> {
            log.debug("添加header {} -> {}", k, v);
            m.addHeader(k, v);
            m.setHeader(k, v);
        });
        // 设置timeout
        RequestConfig config = RequestConfig.custom()
//                .setConnectionRequestTimeout(timeout)
                .setConnectTimeout(timeout)
                .setSocketTimeout(timeout)
                .build();

        m.setConfig(config);
    }

    /**
     * 发送请求获取相应
     *
     * @param m        请求对象
     * @param maxTimes 最大尝试次数
     * @param enc      响应的编码类型，默认utf-8
     * @return 响应字符串
     */
    private static String executeRequest(HttpRequestBase m, Integer maxTimes, String enc) {
        int times = 0;
        maxTimes = maxTimes == null ? MAX_TIMES : maxTimes;
        enc = enc != null ? enc : "utf-8";

        long start = System.currentTimeMillis();
        String result = null;
        String timeoutMsg = "请求超时({}) 地址：{}";
        CloseableHttpClient client = HttpClients.createDefault();

        //执行请求
        while (times < maxTimes) {
            try {
                times++;

                log.debug("第{}次请求 地址：{}", times, m.getURI());

                CloseableHttpResponse response = client.execute(m);

                int statusCode = response.getStatusLine().getStatusCode();
                String msg;

                switch (statusCode) {
                    case HttpStatus.SC_OK:
                        long end = System.currentTimeMillis();
                        log.debug("第{}次请求 成功 地址：{} 耗时：{}", times, m.getURI(), formatDuration(end - start));
                        result = EntityUtils.toString(response.getEntity(), enc);
                        log.debug(result.substring(0, 20));
                        return result;
                    case HttpStatus.SC_BAD_GATEWAY:
                        log.debug("第{}次请求 失败 服务器错误({})", times, statusCode);
                        times--;
                        Thread.sleep(10 * 1000);
                        break;
                    case HttpStatus.SC_INTERNAL_SERVER_ERROR:
                        result = EntityUtils.toString(response.getEntity(), enc);
                        System.err.println(result);
                        throw new IOException(statusCode + " 服务器错误 " + m.getURI());
                    case HttpStatus.SC_NOT_FOUND:
                        throw new IOException(statusCode + " 地址不存在 " + m.getURI());
                    case HttpStatus.SC_MOVED_TEMPORARILY:
                        throw new IOException(statusCode + " 连接被重定向 " + m.getURI());
                    default:
                        throw new IOException(statusCode + " 未定义错误 " + m.getURI());
                }
            } catch (SocketTimeoutException e) {
                if (maxTimes == times) {
                    log.error(timeoutMsg, times, m.getURI());
                } else if ((maxTimes / 3) == times || (maxTimes * 2 / 3) == times) {
                    log.info(timeoutMsg, times, m.getURI());
                } else {
                    log.debug(timeoutMsg, times, m.getURI());
                }
            } catch (IOException e) {
                log.debug("第{}次请求失败 {}", times, e.getMessage());
                break;
            } catch (InterruptedException ignored) {
            }
        }
        return result;
    }

    /**
     * 拼接请求url地址
     *
     * @param urlPrefix url前缀
     * @param urlSuffix url后缀
     * @param portName  接口名
     * @param paramMap  地址栏参数表
     * @param enc       编码类型
     * @return 拼接完成的url地址
     */
    private static String getUrl(String urlPrefix, String urlSuffix,
                                 String portName, Map<String, String[]> paramMap, String enc) {
        urlSuffix = urlSuffix == null ? "" : urlSuffix;
        portName = portName == null ? "" : encode(portName, enc);
        String qs = queryString(paramMap, enc);
        String s = "?";
        String url = urlPrefix + portName + urlSuffix;
        url = (url.contains(s) || "".equals(qs)) ? url : url + s;
        url += qs;
        log.debug("请求地址： " + url);
        return url;
    }

    /**
     * 参数序列化
     *
     * @param paramMap 参数表
     * @return 序列化
     */
    private static String queryString(Map<String, String[]> paramMap, String enc) {
        StringBuilder sb = new StringBuilder();
        if (paramMap == null || paramMap.size() == 0) {
            return "";
        }
        paramMap.forEach((key, value) -> {
            for (String v : value) {
                sb.append("&").append(key).append("=").append(encode(v, enc));
            }
        });
        return sb.toString();
    }

    /**
     * 格式化输出时长 毫秒 秒 分钟
     *
     * @param duration 时长
     * @return 格式化
     */
    private static String formatDuration(long duration) {
        int second = 1000;
        int minute = 60 * second;

        if (duration > minute) {
            return duration / minute + "分钟";
        }
        if (duration > 10 * second) {
            double d = 1.0 * duration / second * 10;
            d = Math.floor(d) / 10;
            return d + "秒";
        }
        return duration + "毫秒";
    }

    /**
     * 解码
     *
     * @param s   待解码字符串
     * @param enc 编码格式 默认utf nga gbk
     * @return 解码完成字符串
     */
    private static String decode(String s, String enc) {
        String encode = null;
        enc = StringUtils.isEmpty(enc) ? "utf-8" : enc;
        try {
            encode = URLDecoder
                    .decode(s, enc);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return encode;
    }

    /**
     * 编码
     *
     * @param s   待编码字符串
     * @param enc 编码格式 默认utf nga gbk
     * @return 编码完成字符串
     */
    public static String encode(String s, String enc) {
        String encode = null;
        enc = StringUtils.isEmpty(enc) ? "utf-8" : enc;
        try {
            encode = URLEncoder
                    .encode(unicodeEncode(s), enc)
                    .replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return encode;
    }

    /**
     * Unicode编码韩文
     *
     * @param str 待编码字符串
     * @return 编码完成字符串
     */
    private static String unicodeEncode(String str) {
        StringBuilder unicode = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            // 取出每一个字符
            char c = str.charAt(i);
            // 韩文转换为unicode
            if (c >= 44032 && c <= 55215) {
                unicode.append("&#").append(Integer.toString(c, 10)).append(";");
            } else {
                unicode.append(c);
            }
        }
        return unicode.toString();
    }


    /**
     * 一次尝试连接
     *
     * @param url   url
     * @param start 文件开头
     * @param end   文件结尾
     * @return 响应对象
     * @throws IOException
     */
    private static CloseableHttpResponse getResponse(String url, Integer start, Integer end) throws IOException {
        CloseableHttpClient client = getCloseableHttpClient();
        HttpGet get = new HttpGet(url);
        int endIndex = url.indexOf("/", url.indexOf("//") + 2);
        get.addHeader("Referer", url.substring(0, endIndex));
        get.addHeader("Range", "bytes=" + (start != null ? start : 0) + "-" + (end != null ? end : ""));
        CloseableHttpResponse execute = client.execute(get);
        return execute;
    }
}
