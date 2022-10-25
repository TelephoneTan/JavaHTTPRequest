package pub.telephone.javahttprequest.network.http;

import okhttp3.*;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import pub.telephone.javahttprequest.network.mime.MIMEType;
import pub.telephone.javahttprequest.util.Util;
import pub.telephone.javapromise.async.promise.Promise;
import pub.telephone.javapromise.async.task.once.OnceTask;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.Proxy;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CountDownLatch;

public class HTTPRequest implements Cloneable {
    public HTTPMethod Method;
    public String URL;
    public List<String[]> CustomizedHeaderList;
    public List<String[]> RequestForm;
    public String RequestString;
    public File RequestFile;
    public RequestBody RequestBody;
    public MIMEType RequestContentType;
    public Duration ConnectTimeout = Duration.ofSeconds(2);
    public Duration ReadTimeout = Duration.ofSeconds(20);
    public Duration WriteTimeout = Duration.ofSeconds(20);
    public boolean IsQuickTest;
    public boolean FollowRedirect = true;
    public HTTPCookieJar CookieJar;
    public Proxy Proxy;
    //
    CountDownLatch enqueued;
    OnceTask<HTTPResult<HTTPRequest>> send;
    OnceTask<HTTPResult<InputStream>> stream;
    OnceTask<HTTPResult<byte[]>> byteArray;
    OnceTask<HTTPResult<String>> string;
    OnceTask<HTTPResult<JSONObject>> jsonObject;
    OnceTask<HTTPResult<JSONArray>> jsonArray;
    OnceTask<HTTPResult<Document>> htmlDocument;

    void init() {
        enqueued = new CountDownLatch(1);
        send = new OnceTask<>((resolver, rejector) -> {
            //
            OkHttpClient.Builder clientBuilder = client.newBuilder();
            //
            Request.Builder requestBuilder = new Request.Builder();
            requestBuilder.url(new URI(URL).toURL());
            //
            if (RequestForm != null && RequestForm.size() > 0) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < RequestForm.size(); i++) {
                    String[] kv = RequestForm.get(i);
                    if (kv != null && kv.length > 0) {
                        if (i > 0) {
                            sb.append("&");
                        }
                        sb.append(URLEncoder.encode(Util.GetEmptyStringFromNull(kv[0]), StandardCharsets.UTF_8.name()));
                        if (kv.length > 1) {
                            sb.append("=")
                                    .append(
                                            URLEncoder.encode(
                                                    Util.GetEmptyStringFromNull(kv[1]), StandardCharsets.UTF_8.name()
                                            )
                                    );
                        }
                    }
                }
                if (sb.length() > 0) {
                    RequestBody = okhttp3.RequestBody.create(
                            sb.toString(),
                            MediaType.parse(MIMEType.X_WWW_Form_URLEncoded.Name)
                    );
                }
            } else if (RequestString != null && !RequestString.isEmpty()) {
                RequestBody = okhttp3.RequestBody.create(
                        RequestString,
                        RequestContentType == null ? null : MediaType.parse(RequestContentType.Name)
                );
            } else if (RequestFile != null) {
                RequestBody = okhttp3.RequestBody.create(
                        RequestFile,
                        RequestContentType == null ? null : MediaType.parse(RequestContentType.Name)
                );
            }
            //
            requestBuilder.method(Method == null ? HTTPMethod.GET.Name : Method.Name, RequestBody);
            //
            if (CustomizedHeaderList != null) {
                for (String[] kv : CustomizedHeaderList) {
                    String key = null;
                    String value = null;
                    if (kv.length > 0) {
                        key = kv[0];
                        if (kv.length > 1) {
                            value = kv[1];
                        }
                    }
                    requestBuilder.addHeader(Util.GetEmptyStringFromNull(key), Util.GetEmptyStringFromNull(value));
                }
            }
            //
            clientBuilder.followSslRedirects(FollowRedirect).followSslRedirects(FollowRedirect);
            //
            if (IsQuickTest) {
                ConnectTimeout = ReadTimeout = WriteTimeout = Duration.ofMillis(500);
            }
            clientBuilder.connectTimeout(ConnectTimeout);
            clientBuilder.readTimeout(ReadTimeout);
            clientBuilder.writeTimeout(WriteTimeout);
            //
            if (CookieJar != null) {
                clientBuilder.cookieJar(CookieJar);
            }
            //
            if (Proxy != null) {
                clientBuilder.proxy(Proxy);
                //
                TrustManager[] trustAllCerts = new TrustManager[]{
                        new X509TrustManager() {
                            @Override
                            public void checkClientTrusted(X509Certificate[] chain, String authType) {
                            }

                            @Override
                            public void checkServerTrusted(X509Certificate[] chain, String authType) {
                            }

                            @Override
                            public X509Certificate[] getAcceptedIssuers() {
                                return new X509Certificate[]{};
                            }
                        }
                };
                SSLContext sslContext = SSLContext.getInstance("SSL");
                sslContext.init(null, trustAllCerts, null);
                clientBuilder.sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustAllCerts[0]);
                clientBuilder.hostnameVerifier((hostname, session) -> true);
            }
            //
            enqueued.countDown();
            clientBuilder.build().newCall(requestBuilder.build()).enqueue(new Callback() {
                @Override
                public void onFailure(@NotNull Call call, @NotNull IOException e) {
                    Util.WaitLatch(enqueued);
                    //
                    rejector.Reject(e);
                }

                @Override
                public void onResponse(@NotNull Call call, @NotNull Response response) {
                    Util.WaitLatch(enqueued);
                    //
                    StatusCode = response.code();
                    StatusMessage = response.message();
                    //
                    Headers headers = response.headers();
                    Map<String, List<String>> multimap = headers.toMultimap();
                    ResponseHeaderMap = new HashMap<>(multimap);
                    //
                    if (!ResponseHeaderMap.isEmpty()) {
                        ResponseHeaderList = new LinkedList<>();
                        for (String key : ResponseHeaderMap.keySet()) {
                            String k = Util.GetEmptyStringFromNull(key);
                            List<String> valueList = ResponseHeaderMap.get(key);
                            if (valueList != null) {
                                for (String value : valueList) {
                                    String v = Util.GetEmptyStringFromNull(value);
                                    ResponseHeaderList.add(new String[]{k, v});
                                }
                            }
                        }
                    }
                    //
                    HTTPRequest.this.response = response;
                    //
                    resolver.Resolve(result(HTTPRequest.this));
                }
            });
        });
        stream = new OnceTask<>((resolver, rejector) ->
                resolver.Resolve(Send().Then(value -> {
                    if (value.Result.response != null) {
                        ResponseBody body = value.Result.response.body();
                        if (body != null) {
                            return result(body.byteStream());
                        } else {
                            throw new Exception("No stream found");
                        }
                    } else {
                        throw new Exception("No response found");
                    }
                }))
        );
        byteArray = new OnceTask<>((resolver, rejector) ->
                resolver.Resolve(Stream().Then(value -> {
                    try {
                        byte[] buf = new byte[1024];
                        int len;
                        ByteArrayOutputStream os = new ByteArrayOutputStream();
                        while ((len = value.Result.read(buf)) > 0) {
                            os.write(buf, 0, len);
                        }
                        return result(os.toByteArray());
                    } finally {
                        value.Result.close();
                    }
                }))
        );
        string = new OnceTask<>((resolver, rejector) ->
                resolver.Resolve(ByteArray().Then(value -> {
                    Charset charset = StandardCharsets.UTF_8;
                    List<String> contentTypeList = Util.MapGet(
                            value.Request.ResponseHeaderMap,
                            HTTPHeader.Content_Type.Name
                    );
                    if (contentTypeList != null && !contentTypeList.isEmpty()) {
                        String contentType = contentTypeList.get(0);
                        MediaType mediaType = MediaType.parse(contentType);
                        if (mediaType != null) {
                            Charset readCharset = mediaType.charset();
                            if (readCharset != null) {
                                charset = readCharset;
                            }
                        }
                    }
                    return result(new String(value.Result, charset));
                }))
        );
        jsonObject = new OnceTask<>((resolver, rejector) ->
                resolver.Resolve(String().Then(value -> result(new JSONObject(value.Result))))
        );
        jsonArray = new OnceTask<>((resolver, rejector) ->
                resolver.Resolve(String().Then(value -> result(new JSONArray(value.Result))))
        );
        htmlDocument = new OnceTask<>((resolver, rejector) ->
                resolver.Resolve(String().Then(value -> result(Jsoup.parse(value.Result))))
        );
    }

    //================================================
    public int StatusCode;
    public String StatusMessage;
    public List<String[]> ResponseHeaderList;
    public Map<String, List<String>> ResponseHeaderMap;
    //
    Response response;
    //------------------------------------------------

    static final OkHttpClient client = new OkHttpClient();

    public HTTPRequest SetCookieJar(HTTPCookieJar cookieJar) {
        CookieJar = cookieJar;
        return this;
    }

    public HTTPRequest SetProxy(java.net.Proxy proxy) {
        Proxy = proxy;
        return this;
    }

    public HTTPRequest SetRequestForm(List<String[]> requestForm) {
        RequestForm = requestForm;
        return this;
    }

    public HTTPRequest(String URL) {
        this.URL = URL;
        init();
    }

    @Override
    public HTTPRequest clone() {
        try {
            HTTPRequest clone = (HTTPRequest) super.clone();
            //
            if (CustomizedHeaderList != null) {
                clone.CustomizedHeaderList = new ArrayList<>(CustomizedHeaderList.size());
                for (String[] v : CustomizedHeaderList) {
                    clone.CustomizedHeaderList.add(v.clone());
                }
            }
            if (RequestForm != null) {
                clone.RequestForm = new ArrayList<>(RequestForm.size());
                for (String[] v : RequestForm) {
                    clone.RequestForm.add(v.clone());
                }
            }
            clone.init();
            //
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }

    <E> HTTPResult<E> result(E result) {
        return new HTTPResult<>(this, result);
    }

    public Promise<HTTPResult<HTTPRequest>> Send() {
        return send.Do();
    }

    public Promise<HTTPResult<InputStream>> Stream() {
        return stream.Do();
    }

    public Promise<HTTPResult<byte[]>> ByteArray() {
        return byteArray.Do();
    }

    public Promise<HTTPResult<String>> String() {
        return string.Do();
    }

    public Promise<HTTPResult<JSONObject>> JSONObject() {
        return jsonObject.Do();
    }

    public Promise<HTTPResult<JSONArray>> JSONArray() {
        return jsonArray.Do();
    }

    public Promise<HTTPResult<Document>> HTMLDocument() {
        return htmlDocument.Do();
    }

    public Promise<HTTPResult<Document>> HTMLDocument(String baseURI) {
        return String().Then(value -> result(Jsoup.parse(value.Result, baseURI)));
    }
}
