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
import java.io.*;
import java.net.Proxy;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static pub.telephone.javahttprequest.network.mime.MIMEType.ApplicationOctetStream;
import static pub.telephone.javahttprequest.network.mime.MIMEType.TextPlain;

public class HTTPRequest implements Cloneable {
    public HTTPMethod Method;
    public String URL;
    public List<String[]> CustomizedHeaderList;
    public byte[] RequestBinary;
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
    int initialized;
    CountDownLatch saved;
    CountDownLatch enqueued;
    CountDownLatch cancelled;
    AtomicReference<Call> call;
    OnceTask<HTTPResult<HTTPRequest>> send;
    OnceTask<HTTPResult<InputStream>> stream;
    OnceTask<HTTPResult<byte[]>> byteArray;
    OnceTask<HTTPResult<String>> string;
    OnceTask<HTTPResult<JSONObject>> jsonObject;
    OnceTask<HTTPResult<JSONArray>> jsonArray;
    OnceTask<HTTPResult<Document>> htmlDocument;

    /**
     * 之所以要将一些初始化步骤放到 init 方法中是因为 clone 方法需要调用 init 方法完成克隆。
     */
    void init() {
        saved = new CountDownLatch(1);
        enqueued = new CountDownLatch(1);
        cancelled = new CountDownLatch(1);
        call = new AtomicReference<>(null);
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
                            MediaType.parse(MIMEType.XWWWFormURLEncoded.Name)
                    );
                }
            } else if (RequestString != null && !RequestString.isEmpty()) {
                RequestBody = okhttp3.RequestBody.create(
                        RequestString,
                        MediaType.parse((RequestContentType == null ? TextPlain : RequestContentType).Name)
                );
            } else if (RequestFile != null) {
                RequestBody = okhttp3.RequestBody.create(
                        RequestFile,
                        MediaType.parse((RequestContentType == null ? ApplicationOctetStream : RequestContentType).Name)
                );
            } else if (RequestBinary != null) {
                RequestBody = okhttp3.RequestBody.create(
                        RequestBinary,
                        MediaType.parse((RequestContentType == null ? ApplicationOctetStream : RequestContentType).Name)
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
            if (cancelled.await(0, TimeUnit.SECONDS)) {
                return;
            }
            //
            enqueued.countDown();
            Call call = clientBuilder.build().newCall(requestBuilder.build());
            call.enqueue(new Callback() {
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
                    send.Do().Catch(reason -> {
                        response.close();
                        return null;
                    }).ForCancel(response::close);
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
                            List<String> valueList = ResponseHeaderMap.get(key);
                            for (String value : valueList) {
                                ResponseHeaderList.add(new String[]{key, value});
                            }
                        }
                    }
                    //
                    HTTPRequest.this.response = response;
                    //
                    resolver.Resolve(result(HTTPRequest.this));
                }
            });
            this.call.set(call);
        });
        stream = new OnceTask<>((resolver, rejector) ->
                resolver.Resolve(send.Do().Then(value -> {
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
                resolver.Resolve(stream.Do().Then(value -> {
                    ByteArrayOutputStream os = new ByteArrayOutputStream();
                    Util.Transfer(value.Result, os);
                    return result(os.toByteArray());
                }))
        );
        string = new OnceTask<>((resolver, rejector) ->
                resolver.Resolve(byteArray.Do().Then(value ->
                        result(new String(value.Result, calculateCharset(value, StandardCharsets.UTF_8)))
                ))
        );
        jsonObject = new OnceTask<>((resolver, rejector) ->
                resolver.Resolve(string.Do().Then(value -> result(new JSONObject(value.Result))))
        );
        jsonArray = new OnceTask<>((resolver, rejector) ->
                resolver.Resolve(string.Do().Then(value -> result(new JSONArray(value.Result))))
        );
        htmlDocument = new OnceTask<>((resolver, rejector) ->
                resolver.Resolve(string.Do().Then(value -> result(Jsoup.parse(value.Result))))
        );
        //
        synchronized (this) {
            initialized++;
        }
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

    public HTTPRequest SetMethod(HTTPMethod method) {
        Method = method;
        return this;
    }

    public HTTPRequest SetRequestBinary(byte[] requestBinary) {
        RequestBinary = requestBinary;
        return this;
    }

    public HTTPRequest SetRequestForm(List<String[]> requestForm) {
        RequestForm = requestForm;
        return this;
    }

    public HTTPRequest SetRequestString(String requestString) {
        RequestString = requestString;
        return this;
    }

    public HTTPRequest SetRequestFile(File requestFile) {
        RequestFile = requestFile;
        return this;
    }

    public HTTPRequest SetProxy(java.net.Proxy proxy) {
        Proxy = proxy;
        return this;
    }

    public HTTPRequest SetConnectTimeout(Duration connectTimeout) {
        ConnectTimeout = connectTimeout;
        return this;
    }

    public HTTPRequest SetReadTimeout(Duration readTimeout) {
        ReadTimeout = readTimeout;
        return this;
    }

    public HTTPRequest SetWriteTimeout(Duration writeTimeout) {
        WriteTimeout = writeTimeout;
        return this;
    }

    public HTTPRequest SetCustomizedHeaderList(List<String[]> customizedHeaderList) {
        CustomizedHeaderList = customizedHeaderList;
        return this;
    }

    public HTTPRequest(String URL) {
        this.URL = URL;
        init();
    }

    <E> HTTPResult<E> result(E result) {
        return new HTTPResult<>(this, result);
    }

    static Charset calculateCharset(HTTPResult<byte[]> value, Charset defaultCharset) {
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
                    return readCharset;
                }
            }
        }
        return defaultCharset;
    }

    // After init ========================================================

    <R> R afterInit(Callable<R> op) {
        synchronized (this) {
            initialized++;
        }
        try {
            return op.call();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    void afterInit(Runnable op) {
        afterInit(() -> {
            op.run();
            return null;
        });
    }

    @Override
    public HTTPRequest clone() {
        return afterInit(() -> {
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
                if (RequestBinary != null) {
                    clone.RequestBinary = RequestBinary.clone();
                }
                clone.init();
                //
                return clone;
            } catch (CloneNotSupportedException e) {
                throw new AssertionError();
            }
        });
    }

    <R> R trigger(Callable<R> op) {
        return afterInit(() -> {
            saved.await(0, TimeUnit.SECONDS);
            return op.call();
        });
    }

    public HTTPRequest Save() {
        return afterInit(() -> {
            saved.countDown();
            return this;
        });
    }

    public void Cancel() {
        afterInit(() -> {
            send.Cancel();
            Call call = this.call.get();
            if (call == null) {
                cancelled.countDown();
            } else {
                call.cancel();
            }
        });
    }

    public Promise<HTTPResult<HTTPRequest>> Send() {
        return trigger(() ->
                send.Do()
                        .Then(value -> {
                            value.Result.response.close();
                            return value;
                        })
        );
    }

    public Promise<HTTPResult<InputStream>> Stream() {
        return trigger(stream::Do);
    }

    public Promise<HTTPResult<byte[]>> ByteArray() {
        return trigger(byteArray::Do);
    }

    public Promise<HTTPResult<String>> String() {
        return trigger(string::Do);
    }

    public Promise<HTTPResult<String>> String(Charset charset) {
        return trigger(() -> byteArray.Do().Then(value ->
                result(new String(value.Result, calculateCharset(value, charset)))
        ));
    }

    public Promise<HTTPResult<JSONObject>> JSONObject() {
        return trigger(jsonObject::Do);
    }

    public Promise<HTTPResult<JSONArray>> JSONArray() {
        return trigger(jsonArray::Do);
    }

    public Promise<HTTPResult<Document>> HTMLDocument() {
        return trigger(htmlDocument::Do);
    }

    public Promise<HTTPResult<Document>> HTMLDocument(Charset charset) {
        return trigger(() -> String(charset).Then(value -> result(Jsoup.parse(value.Result))));
    }

    public Promise<HTTPResult<Document>> HTMLDocument(String baseURI) {
        return trigger(() -> string.Do().Then(value -> result(Jsoup.parse(value.Result, baseURI))));
    }

    public Promise<HTTPResult<Document>> HTMLDocument(String baseURI, Charset charset) {
        return trigger(() -> String(charset).Then(value -> result(Jsoup.parse(value.Result, baseURI))));
    }

    Promise<HTTPResult<File>> file(File file, boolean append) {
        return trigger(() -> stream.Do().Then(value -> {
            Util.Transfer(value.Result, new FileOutputStream(file, append));
            return result(file);
        }));
    }

    public Promise<HTTPResult<File>> File(File file) {
        return file(file, false);
    }

    public Promise<HTTPResult<File>> File(File file, boolean append) {
        return file(file, append);
    }

    // -------------------------------------

    public static HTTPRequest Get(String url) {
        return new HTTPRequest(url).SetMethod(HTTPMethod.GET);
    }

    public static HTTPRequest Post(String url) {
        return new HTTPRequest(url).SetMethod(HTTPMethod.POST);
    }
}
