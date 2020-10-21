package com.gin.pixivmanager.util;

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
import java.util.HashMap;
import java.util.Map;

/**
 * 请求工具类
 *
 * @author bx002
 */

public class Request {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(Request.class);
    public static final String CONTENT_TYPE_JSON = "application/json";
    public static final String CONTENT_TYPE_MULTIPART_FORM_DATA = "multipart/form-data";
    public static final String CONTENT_TYPE_X_WWW_FORM_URLENCODED = "application/x-www-form-urlencoded";
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
     * 请求结果编码 默认utf-8
     */
    private String decodeEnc = "utf-8";
    /**
     * 参数编码 默认utf-8
     */
    private String encodeEnc = "utf-8";
    /**
     * 请求结果
     */
    private String result;
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
    private StringBuilder param = new StringBuilder();
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
                .setConnectionRequestTimeout(connectionRequestTimeout * 10)
                .setConnectTimeout(connectionRequestTimeout)
                .setSocketTimeout(connectionRequestTimeout).build();

        client = HttpClients.custom()
                .setMaxConnTotal(100)
                .setMaxConnPerRoute(100)
                .setDefaultRequestConfig(config)
                .build();
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
        if (v != null && !"".equals(v)) {
            log.debug("设置header {} -> {}", k, v.substring(0, Math.min(v.length(), 40)) + (v.length() > 40 ? "..." : ""));
            header.put(k, v);
        }
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
        if (v != null) {
            log.debug("添加form-data : {} -> {}", k, v);
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
        if (referer != null && !"".equals(referer)) {
            addHeader("Referer", referer);
        } else {
            int endIndex = url.indexOf("/", url.indexOf("//") + 2);
            addHeader("Referer", url.substring(0, endIndex));
        }
        return this;
    }

    /**
     * 设置Origin
     *
     * @param origin Referer
     * @return this
     */
    public Request setOrigin(String origin) {
        if (origin != null && !"".equals(origin)) {
            addHeader("Origin", origin);
        } else {
            int endIndex = url.indexOf("/", url.indexOf("//") + 2);
            addHeader("Origin", url.substring(0, endIndex));
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
            log.debug("添加文件：{} 文件名：{}", name, file.getName());
            entityBuilder.addPart(name, new FileBody(file));
        }
        return this;
    }

    public Request addParam(String k, Object v) {
        log.debug("添加参数 {} ->{}", k, v);
        if (v != null && !"".equals(v)) {
            param.append("&").append(k).append("=").append(encode(String.valueOf(v), encodeEnc));
        }
        return this;
    }





    /*基础方法*/

    /**
     * 构造方法
     *
     * @param url
     */
    private Request(String url) {
        url += url.contains("?") ? "" : "?";
        this.url = url;
        setTimeOutSecond(30);
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
     * @return Request
     */
    public Request get() {
        return execute(new HttpGet(this.url + param));
    }

    /**
     * post方法
     *
     * @return Request
     */
    public Request post() {
        HttpPost method = new HttpPost(this.url + param);
        method.setEntity(entityBuilder.build());
        return execute(method);
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
    private void addProgress(Integer count) {
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

    public String getResult() {
        return result;
    }


    public Request setMaxTimes(Integer maxTimes) {
        this.maxTimes = maxTimes;
        return this;
    }

    public Request setDecodeEnc(String decodeEnc) {
        this.decodeEnc = decodeEnc;
        return this;
    }


    /**
     * 根据正文类型 处理entity
     *
     * @param i           第x次请求
     * @param entity      entity
     * @param contentType 正文类型
     */
    private void handleEntity(int i, HttpEntity entity, String contentType) throws IOException {
        if (contentType.startsWith("image") || contentType.contains("zip") || contentType.contains("photoshop")) {
            int contentLength = Math.toIntExact(entity.getContentLength());
            File parentFile = file.getParentFile();
            if (progressMap != null) {
                progressMap.put("size", contentLength);
                progressMap.put("times", i + 1);
            }
            if (file.exists() && (file.length() == contentLength || contentLength == -1)) {
                log.info("文件已存在且大小相同 跳过 {}", file);
                if (progressMap != null) {
                    progressMap.put("count", progressMap.get("size"));
                }
            } else {
                if (!parentFile.exists()) {
                    String parentFilePath = parentFile.getPath();
                    if (parentFile.mkdirs()) {
                        log.debug("创建文件夹 {}", parentFilePath);
                    } else {
                        log.warn("文件夹创建失败 {}", parentFilePath);
                    }
                }

                long start = System.currentTimeMillis();
                log.debug("第{}次下载 {}", i + 1, file.getName());
//                try {
                InputStream inputStream = entity.getContent();
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                //缓存大小
                byte[] buffer = new byte[4096];
                int r;
                int readLength = 0;
                while ((r = inputStream.read(buffer)) > 0) {
                    output.write(buffer, 0, r);
                    readLength += r;
                    if (progressMap == null) {
                        long percent = readLength * 100L / contentLength;
                        if (readLength / 1000 % 100 == 0) {
                            log.info("{}/{} {}% >> {}", readLength / 1000, contentLength / 1000, percent, file.getPath());
                        }
                    } else {
                        addProgress(r);

                    }
                }

                FileOutputStream fos = new FileOutputStream(file.getPath());
                output.writeTo(fos);
                output.flush();
                output.close();
                fos.close();
                EntityUtils.consume(entity);

                long end = System.currentTimeMillis();
                log.debug("{} 下载完毕 总耗时 {} 平均速度 {}KB/s", file.getName(), timeCost(start, end), contentLength * 1000L / 1024 / (end - start));
//                } catch (ConnectionClosedException e) {
//                    log.warn("连接关闭({}):  {}", i, file.getName());
//                } catch (SocketTimeoutException e) {
//                    log.warn("连接超时({}):  {} ", i, file.getName());
//                } catch (IOException e) {
//                    e.printStackTrace();
//                } finally {
//                    complete();
//                }
            }
        } else {
            if (!contentType.contains("json")) {
                log.info("请求结果非文件: {}", contentType);
            }
            result = EntityUtils.toString(entity, decodeEnc);
        }

    }

    private Request execute(HttpRequestBase method) {
        header.forEach(method::addHeader);

        String timeoutMsg = "请求超时({}) 地址：{}";
        String msg = " 未定义错误 ";
        for (int i = 0; i < maxTimes; i++) {
            try {
                long start = System.currentTimeMillis();
                long end;
                log.debug("第{}次请求 地址：{}", i + 1, method.getURI());
                CloseableHttpResponse response = client.execute(method);
                int statusCode = response.getStatusLine().getStatusCode();
                switch (statusCode) {
                    case HttpStatus.SC_OK:
                        end = System.currentTimeMillis();
                        log.debug("第{}次请求 成功 地址：{} 耗时：{}", i + 1, method.getURI(), timeCost(start, end));
                        HttpEntity entity = response.getEntity();
                        String contentType = response.getEntity().getContentType().getValue();
                        log.debug("响应类型 {}", contentType);
                        if (!contentType.contains("json") && entity.getContentLength() == -1L) {
                            log.warn("第{}次请求 正文大小错误 重新请求 地址：{}", i + 1, method.getURI());
                            if (i < 2) {
                                break;
                            } else {
                                log.warn("第{}次请求 正文大小错误 强行下载  地址：{}", i + 1, method.getURI());
                            }
                        }
                        handleEntity(i, entity, contentType);
                        return this;
                    case HttpStatus.SC_BAD_GATEWAY:
                        log.debug("第{}次请求 失败 服务器错误({})", i + 1, statusCode);
                        try {
                            Thread.sleep(10 * 1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        break;
                    case HttpStatus.SC_INTERNAL_SERVER_ERROR:
                        msg = " 服务器错误 ";
                        throw new RuntimeException(statusCode + msg + method.getURI());
                    case HttpStatus.SC_NOT_FOUND:
                        msg = " 地址不存在 ";
                        throw new RuntimeException(statusCode + msg + method.getURI());
                    case HttpStatus.SC_MOVED_TEMPORARILY:
                        msg = " 连接被重定向 ";
                        throw new RuntimeException(statusCode + msg + method.getURI());
                    default:
                        throw new RuntimeException(statusCode + msg + method.getURI());
                }


            } catch (RuntimeException e) {
                log.warn(e.getMessage());
                e.printStackTrace();
                break;
            } catch (SocketTimeoutException e) {
                if (maxTimes == i + 1) {
                    log.error(timeoutMsg, i + 1, method.getURI());
                } else if ((maxTimes / 3) == i + 1 || (maxTimes * 2 / 3) == i + 1) {
                    log.debug(timeoutMsg, i + 1, method.getURI());
                } else {
                    log.debug(timeoutMsg, i + 1, method.getURI());
                }
                if (progressMap != null) {
                    progressMap.put("count", 0);
                }
            } catch (IOException e) {
                e.printStackTrace();
                if (progressMap != null) {
                    progressMap.put("count", 0);
                }
            }
        }
        return this;
    }

    /**
     * 计算耗时
     *
     * @param start 开始
     * @param end   结束
     * @return 耗时
     */
    public static String timeCost(long start, long end) {
        long l = end - start;
        int k = 1000;

        if (l > 60 * k) {
            double d = fixTo(l * 1.0 / 60 / k, 1);
            return d + " 分";
        }
        if (l > 10 * k) {
            return l / k + " 秒";
        }

        return l + " 毫秒";
    }

    /**
     * 保留小数
     *
     * @param f 待处理数
     * @param x 位数
     * @return 结果
     */
    public static double fixTo(double f, int x) {
        for (int i = 0; i < x; i++) {
            f *= 10;
        }
        f = Math.floor(f);
        for (int i = 0; i < x; i++) {
            f /= 10;
        }
        return f;
    }

    public void setEncodeEnc(String encodeEnc) {
        this.encodeEnc = encodeEnc;
    }

    public static Map<String, Integer> createProgressMap(int size) {
        Map<String, Integer> map = new HashMap<>();
        map.put("count", 0);
        map.put("times", 1);
        map.put("size", size);
        return map;
    }

    public static void main(String[] args) {


    }
}
