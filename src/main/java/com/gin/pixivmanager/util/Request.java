package com.gin.pixivmanager.util;

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

import java.io.*;
import java.net.SocketTimeoutException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.*;

/**
 * 请求工具类
 *
 * @author bx002
 */

public class Request {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(Request.class);
    public static final String CONTENT_TYPE_JSON = "application/json";
    public static final String CONTENT_TYPE_MULTIPART_FORM_DATA = "multipart/form-data";
    private static final ContentType CONTENT_TYPE_TEXT_PLAIN = ContentType.create(ContentType.TEXT_PLAIN.getMimeType(), Consts.UTF_8);


    /**
     * url
     */
    private final String url;
    private CloseableHttpClient client;
    /**
     * 最大尝试次数
     */
    private Integer maxTimes = 10;
    /**
     * 请求结果编码
     */
    private String enc = "utf-8";
    /**
     * 请求结果
     */
    private Object result;
    /**
     * 如果请求地址为文件的下载地址
     */
    private File file;
    /**
     * 用于输出下载进度的map
     */
    private Map<String, Integer> progressMap;
    /**
     * header
     */
    private Map<String, String> header = new HashMap<>();
    /**
     * 地址栏参数
     */
    private Map<String, String[]> paramMap = new HashMap<>();
    private MultipartEntityBuilder entityBuilder;
    private static final Map<String, String> DEFAULT_HEADERS = new HashMap<>();

    static {

        DEFAULT_HEADERS.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/84.0.4147.135 Safari/537.36");
        DEFAULT_HEADERS.put("Accept-Language", "zh-CN,zh;q=0.9");
    }

    /*公开方法*/

    /**
     * 设置 超时时长
     *
     * @param t 时长
     * @return this
     */
    public Request setTimeOutSecond(Integer t) {
        int connectionRequestTimeout = t * 1000;
        RequestConfig config = RequestConfig.custom()
                .setConnectionRequestTimeout(connectionRequestTimeout)
                .setConnectTimeout(connectionRequestTimeout)
                .setSocketTimeout(connectionRequestTimeout).build();

        client = HttpClients.custom().setDefaultRequestConfig(config).build();
        return this;
    }

    /**
     * 添加Header
     *
     * @param k key
     * @param v value
     * @return this
     */
    public Request addHeader(String k, String v) {
        log.info("设置header {} -> {}", k, v.substring(0, Math.min(v.length(), 40)) + (v.length() > 40 ? "..." : ""));
        header.put(k, v);
        return this;
    }

    /**
     * 添加Header
     *
     * @param map map
     * @return this
     */
    public Request addHeaders(Map<String, String> map) {
        map.forEach(this::addHeader);
        return this;
    }

    /**
     * 设置  content-type
     *
     * @param type content-type
     * @return this
     */
    public Request setContentType(String type) {
        return addHeader("Content-Type", type);
    }

    /**
     * 设置cookie
     *
     * @param cookie cookie
     * @return this
     */
    public Request setCookie(String cookie) {
        return addHeader("cookie", cookie);
    }

    /**
     * 添加表单键值对
     *
     * @param k key
     * @param v value
     * @return this
     */
    public Request addFormData(String k, String v) {
        entityBuilder = entityBuilder == null ? MultipartEntityBuilder.create() : entityBuilder;
        setContentType(CONTENT_TYPE_MULTIPART_FORM_DATA);
        if (v != null && !"".equals(v)) {
            log.info("添加form-data : {} -> {}", k, v);
            entityBuilder.addPart(k, new StringBody(v, CONTENT_TYPE_TEXT_PLAIN));
        }
        return this;
    }

    /**
     * 设置Referer
     *
     * @param referer Referer
     * @return this
     */
    public Request setReferer(String referer) {
        if (referer == null && "".equals(referer)) {
            addHeader("Referer", referer);
        } else {
            int endIndex = url.indexOf("/", url.indexOf("//") + 2);
            addHeader("Referer", url.substring(0, endIndex));
        }
        return this;
    }

    /**
     * 添加表单键值对
     *
     * @param map map
     * @return this
     */
    public Request addFormData(Map<String, String> map) {
        map.forEach(this::addFormData);
        return this;
    }

    /**
     * 添加上传文件
     *
     * @param name 字段名
     * @param file 文件
     * @return this
     */
    public Request addUploadFile(String name, File file) {
        entityBuilder = entityBuilder == null ? MultipartEntityBuilder.create() : entityBuilder;
        if (!file.exists()) {
            log.error("文件不存在 {}", file.getPath());
        } else if (name != null && !"".equals(name)) {
            log.info("添加文件：{} 文件名：{}", name, file.getName());
            entityBuilder.addPart(name, new FileBody(file));
        }
        return this;
    }

    public Request addParam(String k, String v) {
        List<String> list;
        String[] key = paramMap.get(k);
        if (key == null) {
            list = new ArrayList<>();
        } else {
            list = Arrays.asList(key);
        }
        list.add(v);
        String[] s = new String[list.size()];
        for (int i = 0; i < list.size(); i++) {
            s[i] = list.get(i);
        }
        paramMap.put(k, s);
        return this;
    }





















    /*基础方法*/

    /**
     * 构造方法
     *
     * @param url
     */
    private Request(String url) {
        this.url = url;
        setTimeOutSecond(15);
        addDefaultHeaders();
    }

    public static Request create(String url) {
        return new Request(url);
    }

    /**
     * 添加默认Headers
     *
     * @return this
     */
    private Request addDefaultHeaders() {
        return addHeaders(DEFAULT_HEADERS);
    }

    /**
     * get方法
     *
     * @param enc 参数编码 为null时默认utf-8
     * @return Request
     */
    public Request get(String enc) {
        return execute(new HttpGet(addParamMap(this.url, paramMap, enc)));
    }

    /**
     * post方法
     *
     * @param enc 参数编码 为null时默认utf-8
     * @return Request
     */
    public Request post(String enc) {
        return execute(new HttpPost(addParamMap(this.url, paramMap, enc)));
    }

    /**
     * 解码
     *
     * @param s   待解码字符串
     * @param enc 编码格式 默认utf nga gbk
     * @return 解码完成字符串
     */
    public static String decode(String s, String enc) {
        String encode = null;
        enc = enc == null || "".equals(enc) ? "utf-8" : enc;
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
        enc = enc == null || "".equals(enc) ? "utf-8" : enc;
        try {
            encode = URLEncoder
                    .encode(s, enc)
                    .replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return encode;
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
     * 为url添加参数表
     *
     * @param url      url
     * @param paramMap 地址栏参数表
     * @param enc      参数编码 为null时默认utf-8
     * @return 添加好的url
     */
    private static String addParamMap(String url, Map<String, String[]> paramMap, String enc) {
        if (paramMap != null && paramMap.size() > 0) {
            url += url.endsWith("?") ? "" : "?";
            url += queryString(paramMap, enc);
        }
        return url;
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


    public Request setFile(File file) {
        this.file = file;
        return this;
    }

    public Request setProgressMap(Map<String, Integer> progressMap) {
        this.progressMap = progressMap;
        return this;
    }

    /**
     * 更新进度
     *
     * @param count 增加的进度
     */
    private void updateProgress(Integer count) {
        Integer c = progressMap.get("count");
        c += count;
        progressMap.put("count", c);
    }

    /**
     * 下载结束
     */
    public void complete() {
        progressMap.put("count", progressMap.get("size"));
    }

    public Object getResult() {
        return result;
    }


    public void setMaxTimes(Integer maxTimes) {
        this.maxTimes = maxTimes;
    }

    public void setEnc(String enc) {
        this.enc = enc;
    }


    /**
     * 根据正文类型 处理entity
     *
     * @param i           第x次请求
     * @param entity      entity
     * @param contentType 正文类型
     */
    private void handleEntity(int i, HttpEntity entity, String contentType) {
        if (contentType.startsWith("image") || contentType.endsWith("zip")) {
            File parentFile = file.getParentFile();
            long contentLength = entity.getContentLength();
            if (file.exists() && file.length() == contentLength) {
                log.info("文件已存在且大小相同 跳过 {}", file);
                result = file;
            } else if (!parentFile.exists()) {
                String parentFilePath = parentFile.getPath();
                if (parentFile.mkdirs()) {
                    log.info("创建文件夹 {}", parentFilePath);
                } else {
                    log.warn("文件夹创建失败 {}", parentFilePath);
                }
            } else {
                progressMap = progressMap == null ? new HashMap<>() : progressMap;
                progressMap.put("size", Math.toIntExact(contentLength));
                progressMap.put("count", 0);

                long start = System.currentTimeMillis();
                log.info("第{}次下载 {}", i, file.getName());
                try {
                    InputStream inputStream = entity.getContent();
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    //缓存大小
                    byte[] buffer = new byte[4096];
                    int r;
                    while ((r = inputStream.read(buffer)) > 0) {
                        output.write(buffer, 0, r);
                        updateProgress(r);
                    }

                    FileOutputStream fos = new FileOutputStream(file.getPath());
                    output.writeTo(fos);
                    output.flush();
                    output.close();
                    fos.close();
                    EntityUtils.consume(entity);

                    long end = System.currentTimeMillis();
                    log.info("{} 下载完毕 总耗时 {} 秒 平均速度 {}KB/s", file.getName(), (end - start) / 1000, contentLength * 1000L / 1024 / (end - start));
                    result = file;

                } catch (ConnectionClosedException e) {
                    log.warn("连接关闭({}):  {}", i, file.getName());
                } catch (SocketTimeoutException e) {
                    log.warn("连接超时({}):  {} ", i, file.getName());
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    complete();
                }
            }
        } else {
            try {
                result = EntityUtils.toString(entity, enc);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    private Request execute(HttpRequestBase method) {
        header.forEach(method::addHeader);

        String timeoutMsg = "请求超时({}) 地址：{}";
        String msg = " 未定义错误 ";
        int times = 1;
        for (int i = 0; i < times; i++) {
            try {
                long start = System.currentTimeMillis();
                long end;
                log.info("第{}次请求 地址：{}", times, method.getURI());
                CloseableHttpResponse response = client.execute(method);
                int statusCode = response.getStatusLine().getStatusCode();
                switch (statusCode) {
                    case HttpStatus.SC_OK:
                        end = System.currentTimeMillis();
                        log.info("第{}次请求 成功 地址：{} 耗时：{}", times, method.getURI(), formatDuration(end - start));
                        HttpEntity entity = response.getEntity();
                        String contentType = response.getEntity().getContentType().getValue();
                        log.info("响应类型 {}", contentType);
                        handleEntity(i, entity, contentType);
                        return this;
                    case HttpStatus.SC_BAD_GATEWAY:
                        log.info("第{}次请求 失败 服务器错误({})", times, statusCode);
                        try {
                            Thread.sleep(10 * 1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        break;
                    case HttpStatus.SC_INTERNAL_SERVER_ERROR:
                        msg = " 服务器错误 ";
                    case HttpStatus.SC_NOT_FOUND:
                        msg = " 地址不存在 ";
                    case HttpStatus.SC_MOVED_TEMPORARILY:
                        msg = " 连接被重定向 ";
                    default:
                        throw new RuntimeException(statusCode + msg + method.getURI());
                }


            } catch (RuntimeException e) {
                log.info(e.getMessage());
                e.printStackTrace();
                break;
            } catch (SocketTimeoutException e) {
                if (maxTimes == times) {
                    log.error(timeoutMsg, times, method.getURI());
                } else if ((maxTimes / 3) == times || (maxTimes * 2 / 3) == times) {
                    log.info(timeoutMsg, times, method.getURI());
                } else {
                    log.info(timeoutMsg, times, method.getURI());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return this;
    }

    public static void main(String[] args) {

    }
}
