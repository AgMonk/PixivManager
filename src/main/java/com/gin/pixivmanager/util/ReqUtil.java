package com.gin.pixivmanager.util;

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
     * 尝试下载文件
     *
     * @param url      url
     * @param filePath 保存文件的完整地址
     * @return File
     */
    public static File download(String url, String filePath) throws IOException {
        //创建目录
        File file = new File(filePath);


        DataManager dataManager = SpringContextUtil.getBean(DataManager.class);

        int endIndex = url.indexOf("/", url.indexOf("//") + 2);
        String tempName = url.substring(url.lastIndexOf('/') + 1);
        HttpGet get = new HttpGet(url);
        //伪造Referer
        get.addHeader("Referer", url.substring(0, endIndex));
        HEADERS_DEFUALT.forEach(get::addHeader);

        CloseableHttpResponse response = null;
        long start = System.currentTimeMillis();
        for (int i = 1; i <= MAX_TIMES; i++) {
            log.info("第{}次下载 {}", i, tempName);
            String questName = "(" + i + ")" + tempName;
            try {
                int connectionRequestTimeout = 15 * 1000;
                RequestConfig config = RequestConfig.custom()
                        .setConnectionRequestTimeout(connectionRequestTimeout)
                        .setConnectTimeout(connectionRequestTimeout)
                        .setSocketTimeout(connectionRequestTimeout).build();

                CloseableHttpClient client = HttpClients.custom()
                        .setDefaultRequestConfig(config).build();
                response = client.execute(get);
                HttpEntity entity = response.getEntity();
                long contentLength = entity.getContentLength();
                if (file.exists() && file.length() == contentLength) {
                    //文件已存在且大小相同
                    log.info("文件已存在且大小相同 跳过 {}", file);
                    return file;
                }
                InputStream inputStream = entity.getContent();
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                //缓存大小
                byte[] buffer = new byte[4096];
                int r = 0;
                long totalRead = 0;
                while ((r = inputStream.read(buffer)) > 0) {
                    output.write(buffer, 0, r);
                    totalRead += r;

                    //下载进度
                    dataManager.addDownloading(questName, contentLength - totalRead, contentLength);
                }
                File parentFile = file.getParentFile();
                if (!parentFile.exists()) {
                    parentFile.mkdirs();
                }
                FileOutputStream fos = new FileOutputStream(filePath);
                output.writeTo(fos);
                output.flush();
                output.close();
                fos.close();
                EntityUtils.consume(entity);
                long end = System.currentTimeMillis();
                log.info("{} 下载完毕 总耗时 {} 秒 平均速度 {}KB/s", tempName, (end - start) / 1000, contentLength / (end - start));

                //下载成功 清空所有进度
                for (int j = 1; j <= MAX_TIMES; j++) {
                    String name = "(" + j + ")" + tempName;
                    dataManager.addDownloading(name, 0, 1);
                }

                return file;
            } catch (ConnectionClosedException e) {
                log.warn("连接关闭({}):  {}", i, url);
                dataManager.addDownloading(questName, 0, 1);
            } catch (SocketTimeoutException e) {
                log.warn("连接超时({}):  {} ", i, url);
                dataManager.addDownloading(questName, 0, 1);
            } catch (IOException e) {
                log.warn("下载失败({}): {}", i, url);
                dataManager.addDownloading(questName, 0, 1);
                e.printStackTrace();
            }
        }
        log.warn("下载失败 {}", url);
        throw new IOException("下载失败 超出最大次数 " + MAX_TIMES);
    }

    /**
     * post请求
     */
    public static String post(String urlPrefix, String urlSuffix,
                              String portName, Map<String, String[]> paramMap,
                              String cookie, Integer timeout,
                              Map<String, String> formData, Map<String, File> fileMap,
                              Integer maxTimes, String enc) {

        Map<String, String> headers = new HashMap<>();
        headers.putAll(HEADERS_DEFUALT);
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
        Map<String, String> headers = new HashMap<>();
        headers.putAll(HEADERS_DEFUALT);
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
                log.debug("添加Form-Data {} -> {}", k, v);
                builder.addPart(k, new StringBody(v, contentType));
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
        lableA:
        while (times < maxTimes) {
            try {
                times++;

                log.debug("第{}次请求 地址：{}", times, m.getURI());

                CloseableHttpResponse response = client.execute(m);

                int statusCode = response.getStatusLine().getStatusCode();

                switch (statusCode) {
                    case HttpStatus.SC_BAD_GATEWAY:
                        log.debug("第{}次请求 失败 服务器错误({})", times, statusCode);
                        times--;
                        Thread.sleep(10 * 1000);
                        break;
                    case HttpStatus.SC_MOVED_TEMPORARILY:
                        log.info("第{}次请求 连接被重定向({}) {}", times, statusCode, m.getURI());
                        break lableA;
                    case HttpStatus.SC_OK:
                        long end = System.currentTimeMillis();
                        log.debug("第{}次请求 成功 地址：{} 耗时：{}", times, m.getURI(), formatDuration(end - start));
                        result = EntityUtils.toString(response.getEntity(), enc);
                        log.debug(result.substring(0, 20));
                        break lableA;
                    case HttpStatus.SC_NOT_FOUND:
                        log.debug("第{}次请求 失败 地址不存在({}) 地址：{} ", times, statusCode, m.getURI());
                        break lableA;
                    default:
                        log.info("第{}次请求 未定义错误({})", times, statusCode);
                        break lableA;
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
                log.error("请求失败 地址：{}", m.getURI());
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
                unicode.append("&#" + Integer.toString(c, 10) + ";");
            } else {
                unicode.append(c);
            }
        }
        return unicode.toString();
    }


}
