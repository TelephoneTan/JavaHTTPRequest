package pub.telephone.javahttprequest.network.http;

import okhttp3.*;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import pub.telephone.javahttprequest.network.NetworkProxy;
import pub.telephone.javahttprequest.network.mime.MIMEType;
import pub.telephone.javahttprequest.util.Util;
import pub.telephone.javapromise.async.promise.Promise;
import pub.telephone.javapromise.async.promise.PromiseSemaphore;
import pub.telephone.javapromise.async.task.once.OnceTask;

import java.io.*;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static pub.telephone.javahttprequest.network.mime.MIMEType.ApplicationOctetStream;
import static pub.telephone.javahttprequest.network.mime.MIMEType.TextPlainUTF8;

public class HTTPRequest implements Cloneable {
    static final OkHttpClient client = new OkHttpClient();
    static final boolean defaultIsQuickTest = false;
    static final boolean defaultFollowRedirect = true;
    static final int defaultStatusCode = 0;
    static final Duration defaultConnectTimeout = Duration.ofSeconds(2);
    static final Duration defaultReadTimeout = Duration.ofSeconds(20);
    static final Duration defaultWriteTimeout = Duration.ofSeconds(20);
    //
    public final PromiseSemaphore RequestSemaphore;
    public HTTPMethod Method;
    public String URL;
    public List<String[]> CustomizedHeaderList;
    public byte[] RequestBinary;
    public List<String[]> RequestForm;
    public String RequestString;
    public File RequestFile;
    public RequestBody RequestBody;
    public MIMEType RequestContentType;
    public String RequestContentTypeHeader;
    public Duration Timeout;
    public Duration ConnectTimeout = defaultConnectTimeout;
    public Duration ReadTimeout = defaultReadTimeout;
    public Duration WriteTimeout = defaultWriteTimeout;
    public boolean IsQuickTest = defaultIsQuickTest;
    public boolean FollowRedirect = defaultFollowRedirect;
    public HTTPFlexibleCookieJar CookieJar;
    public Boolean AutoSendCookies;
    public Boolean AutoReceiveCookies;
    public NetworkProxy Proxy;
    //================================================
    public int StatusCode = defaultStatusCode;
    public String StatusMessage;
    public List<String[]> ResponseHeaderList;
    public Map<String, List<String>> ResponseHeaderMap;
    //
    byte[] responseBinary;
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
    //
    Response response;
    //------------------------------------------------

    public HTTPRequest(HTTPMethod method, String url, PromiseSemaphore requestSemaphore) {
        this.RequestSemaphore = requestSemaphore;
        this.Method = method;
        this.URL = url;
        init();
    }

    public HTTPRequest(HTTPMethod method, String url) {
        this.RequestSemaphore = null;
        this.Method = method;
        this.URL = url;
        init();
    }

    public HTTPRequest(String url, PromiseSemaphore requestSemaphore) {
        this.RequestSemaphore = requestSemaphore;
        this.URL = url;
        init();
    }

    public HTTPRequest(String url) {
        this.RequestSemaphore = null;
        this.URL = url;
        init();
    }

    public HTTPRequest(PromiseSemaphore requestSemaphore) {
        this.RequestSemaphore = requestSemaphore;
        init();
    }

    public HTTPRequest() {
        this.RequestSemaphore = null;
        init();
    }

    static Charset calculateCharset(HTTPResult<byte[]> value, Charset defaultCharset) {
        List<String> contentTypeList = Util.MapGet(
                value.Request.ResponseHeaderMap,
                HTTPHeader.ContentType.Name
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

    public static HTTPRequest Get(String url) {
        return new HTTPRequest(url).SetMethod(HTTPMethod.GET);
    }

    public static HTTPRequest Post(String url) {
        return new HTTPRequest(url).SetMethod(HTTPMethod.POST);
    }

    static Object serializeHeaderList(List<String[]> headerList) {
        return headerList == null ? JSONObject.NULL : new JSONArray() {{
            for (String[] kv : headerList) {
                if (kv == null) {
                    put(JSONObject.NULL);
                    continue;
                }
                put(new JSONArray() {{
                    if (kv.length > 0) {
                        String k = kv[0];
                        put(k == null ? JSONObject.NULL : k);
                        if (kv.length > 1) {
                            String v = kv[1];
                            put(v == null ? JSONObject.NULL : v);
                        }
                    }
                }});
            }
        }};
    }

    static List<String[]> deserializeHeaderList(JSONObject jo, String key) {
        return jo.isNull(key) ? null : new ArrayList<String[]>() {{
            JSONArray ja = jo.getJSONArray(key);
            for (int i = 0; i < ja.length(); i++) {
                if (ja.isNull(i)) {
                    add(null);
                    continue;
                }
                JSONArray kv = ja.getJSONArray(i);
                add(new ArrayList<String>() {{
                    if (kv.length() > 0) {
                        add(kv.isNull(0) ? null : kv.getString(0));
                        if (kv.length() > 1) {
                            add(kv.isNull(1) ? null : kv.getString(1));
                        }
                    }
                }}.toArray(new String[0]));
            }
        }};
    }

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
                    RequestBody = okhttp3.RequestBody.create(sb.toString(), MediaType.parse(MIMEType.XWWWFormURLEncoded.Name));
                }
            } else if (RequestString != null && !RequestString.isEmpty()) {
                RequestBody = okhttp3.RequestBody.create(
                        RequestString,
                        MediaType.parse(
                                RequestContentType == null ?
                                        (RequestContentTypeHeader == null || RequestContentTypeHeader.isEmpty() ?
                                                TextPlainUTF8.Name :
                                                RequestContentTypeHeader
                                        ) :
                                        RequestContentType.Name
                        )
                );
            } else if (RequestFile != null) {
                RequestBody = okhttp3.RequestBody.create(RequestFile, MediaType.parse(
                                RequestContentType == null ?
                                        (RequestContentTypeHeader == null || RequestContentTypeHeader.isEmpty() ?
                                                ApplicationOctetStream.Name :
                                                RequestContentTypeHeader
                                        ) :
                                        RequestContentType.Name
                        )
                );
            } else if (RequestBinary != null && RequestBinary.length > 0) {
                RequestBody = okhttp3.RequestBody.create(RequestBinary, MediaType.parse(
                                RequestContentType == null ?
                                        (RequestContentTypeHeader == null || RequestContentTypeHeader.isEmpty() ?
                                                ApplicationOctetStream.Name :
                                                RequestContentTypeHeader
                                        ) :
                                        RequestContentType.Name
                        )
                );
            }
            //
            if (Method == null) {
                Method = HTTPMethod.GET;
            }
            //
            switch (Method) {
                case PUT:
                case POST:
                case PATCH:
                    if (RequestBody == null) {
                        RequestBody = okhttp3.RequestBody.create(new byte[0]);
                    }
            }
            //
            requestBuilder.method(Method.Name, RequestBody);
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
            clientBuilder.followRedirects(FollowRedirect).followSslRedirects(FollowRedirect);
            //
            if (Timeout == null) {
                Timeout = ConnectTimeout.plus(ReadTimeout).plus(WriteTimeout);
            }
            if (IsQuickTest) {
                ConnectTimeout = ReadTimeout = WriteTimeout = Duration.ofMillis(500);
                Timeout = ConnectTimeout.plus(ReadTimeout).plus(WriteTimeout);
            }
            clientBuilder.callTimeout(Timeout);
            clientBuilder.connectTimeout(ConnectTimeout);
            clientBuilder.readTimeout(ReadTimeout);
            clientBuilder.writeTimeout(WriteTimeout);
            //
            if (CookieJar != null) {
                if (AutoSendCookies != null && AutoReceiveCookies != null) {
                    CookieJar = CookieJar.WithReadWrite(AutoSendCookies, AutoReceiveCookies);
                } else if (AutoSendCookies != null) {
                    CookieJar = CookieJar.WithRead(AutoSendCookies);
                } else if (AutoReceiveCookies != null) {
                    CookieJar = CookieJar.WithWrite(AutoReceiveCookies);
                }
                clientBuilder.cookieJar(CookieJar);
            }
            //
            if (Proxy != null) {
                clientBuilder.proxy(Proxy.Proxy());
            } else {
                clientBuilder.proxySelector(ProxySelector.getDefault());
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
                    ResponseHeaderMap = new HashMap<>();
                    for (String key : multimap.keySet()) {
                        List<String> value = multimap.get(key);
                        if (key != null) {
                            key = key.toLowerCase();
                        }
                        ResponseHeaderMap.put(key, value);
                    }
                    //
                    ResponseHeaderList = new ArrayList<>();
                    if (!ResponseHeaderMap.isEmpty()) {
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
            this.call.updateAndGet(prev -> {
                try {
                    if (cancelled.await(0, TimeUnit.SECONDS)) {
                        call.cancel();
                    }
                } catch (InterruptedException ignored) {
                }
                return call;
            });
        }, RequestSemaphore);
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
                    value.Request.responseBinary = os.toByteArray();
                    return result(value.Request.responseBinary);
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

    public HTTPRequest ToGet() {
        return SetMethod(HTTPMethod.GET);
    }

    public HTTPRequest ToGet(String url) {
        return SetMethod(HTTPMethod.GET).SetURL(url);
    }

    public HTTPRequest ToPost() {
        return SetMethod(HTTPMethod.POST);
    }

    public HTTPRequest ToPost(String url) {
        return SetMethod(HTTPMethod.POST).SetURL(url);
    }

    public List<String> GetHeader(String name) {
        return ResponseHeaderMap.get(name.toLowerCase());
    }

    public HTTPRequest SetURL(String url) {
        this.URL = url;
        return this;
    }

    public HTTPRequest SetCookieJar(HTTPFlexibleCookieJar cookieJar) {
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

    public HTTPRequest SetProxy(NetworkProxy proxy) {
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

    public HTTPRequest SetRequestBody(RequestBody requestBody) {
        RequestBody = requestBody;
        return this;
    }

    public HTTPRequest SetRequestContentType(MIMEType requestContentType) {
        RequestContentType = requestContentType;
        return this;
    }

    public HTTPRequest SetRequestContentTypeHeader(String requestContentTypeHeader) {
        RequestContentTypeHeader = requestContentTypeHeader;
        return this;
    }

    public HTTPRequest SetQuickTest(boolean quickTest) {
        IsQuickTest = quickTest;
        return this;
    }

    public HTTPRequest SetFollowRedirect(boolean followRedirect) {
        FollowRedirect = followRedirect;
        return this;
    }

    public HTTPRequest SetTimeout(Duration timeout) {
        Timeout = timeout;
        return this;
    }

    public HTTPRequest SetAutoSendCookies(Boolean autoSendCookies) {
        AutoSendCookies = autoSendCookies;
        return this;
    }

    public HTTPRequest SetAutoReceiveCookies(Boolean autoReceiveCookies) {
        AutoReceiveCookies = autoReceiveCookies;
        return this;
    }

    <E> HTTPResult<E> result(E result) {
        return new HTTPResult<>(this, result);
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

    public String Serialize() {
        JSONObject jo = new JSONObject();
        jo.put("Method", Method == null ? JSONObject.NULL : Method.name());
        jo.put("URL", URL == null ? JSONObject.NULL : URL);
        jo.put("CustomizedHeaderList", serializeHeaderList(CustomizedHeaderList));
        jo.put("RequestBinary", RequestBinary == null ? JSONObject.NULL : Base64.getEncoder().encodeToString(RequestBinary));
        jo.put("RequestForm", serializeHeaderList(RequestForm));
        jo.put("RequestString", RequestString == null ? JSONObject.NULL : RequestString);
        jo.put("RequestContentType", RequestContentType == null ? JSONObject.NULL : RequestContentType.name());
        jo.put("RequestContentTypeHeader", RequestContentTypeHeader == null ? JSONObject.NULL : RequestContentTypeHeader);
        jo.put("Timeout", Timeout == null ? JSONObject.NULL : Timeout.toMillis());
        jo.put("ConnectTimeout", ConnectTimeout == null ? JSONObject.NULL : ConnectTimeout.toMillis());
        jo.put("ReadTimeout", ReadTimeout == null ? JSONObject.NULL : ReadTimeout.toMillis());
        jo.put("WriteTimeout", WriteTimeout == null ? JSONObject.NULL : WriteTimeout.toMillis());
        jo.put("IsQuickTest", IsQuickTest);
        jo.put("FollowRedirect", FollowRedirect);
        jo.put("AutoSendCookies", AutoSendCookies == null ? JSONObject.NULL : AutoSendCookies);
        jo.put("AutoReceiveCookies", AutoReceiveCookies == null ? JSONObject.NULL : AutoReceiveCookies);
        jo.put("Proxy", Proxy == null ? JSONObject.NULL : new JSONObject() {{
            put("Type", Proxy.Type == null ? JSONObject.NULL : Proxy.Type.name());
            put("Host", Proxy.Host == null ? JSONObject.NULL : Proxy.Host);
            put("Port", Proxy.Port == null ? JSONObject.NULL : Proxy.Port);
        }});
        jo.put("StatusCode", StatusCode);
        jo.put("StatusMessage", StatusMessage == null ? JSONObject.NULL : StatusMessage);
        jo.put("ResponseHeaderList", serializeHeaderList(ResponseHeaderList));
        jo.put("ResponseHeaderMap", ResponseHeaderMap == null ? JSONObject.NULL : new JSONArray() {{
            for (String k : ResponseHeaderMap.keySet()) {
                List<String> v = ResponseHeaderMap.get(k);
                put(new JSONArray() {{
                    put(k == null ? JSONObject.NULL : k);
                    put(v == null ? JSONObject.NULL : new JSONArray() {{
                        for (String ve : v) {
                            put(ve == null ? JSONObject.NULL : ve);
                        }
                    }});
                }});
            }
        }});
        jo.put("responseBinary", responseBinary == null ? JSONObject.NULL : Base64.getEncoder().encodeToString(responseBinary));
        return jo.toString();
    }

    public HTTPRequest Deserialize(String s) {
        Object obj = new JSONTokener(s).nextValue();
        if (!(obj instanceof JSONObject)) {
            return this;
        }
        JSONObject jo = (JSONObject) obj;
        {
            String key = "Method";
            if (jo.has(key)) {
                Method = jo.isNull(key) ? null : HTTPMethod.valueOf(jo.getString(key));
            }
        }
        {
            String key = "URL";
            if (jo.has(key)) {
                URL = jo.isNull(key) ? null : jo.getString(key);
            }
        }
        {
            String key = "CustomizedHeaderList";
            if (jo.has(key)) {
                CustomizedHeaderList = deserializeHeaderList(jo, key);
            }
        }
        {
            String key = "RequestBinary";
            if (jo.has(key)) {
                RequestBinary = jo.isNull(key) ? null : Base64.getDecoder().decode(jo.getString(key));
            }
        }
        {
            String key = "RequestForm";
            if (jo.has(key)) {
                RequestForm = deserializeHeaderList(jo, key);
            }
        }
        {
            String key = "RequestString";
            if (jo.has(key)) {
                RequestString = jo.isNull(key) ? null : jo.getString(key);
            }
        }
        {
            String key = "RequestContentType";
            if (jo.has(key)) {
                RequestContentType = jo.isNull(key) ? null : MIMEType.valueOf(jo.getString(key));
            }
        }
        {
            String key = "RequestContentTypeHeader";
            if (jo.has(key)) {
                RequestContentTypeHeader = jo.isNull(key) ? null : jo.getString(key);
            }
        }
        {
            String key = "Timeout";
            if (jo.has(key)) {
                Timeout = jo.isNull(key) ? null : Duration.ofMillis(jo.getLong(key));
            }
        }
        {
            String key = "ConnectTimeout";
            if (jo.has(key)) {
                ConnectTimeout = jo.isNull(key) ? defaultConnectTimeout : Duration.ofMillis(jo.getLong(key));
            }
        }
        {
            String key = "ReadTimeout";
            if (jo.has(key)) {
                ReadTimeout = jo.isNull(key) ? defaultReadTimeout : Duration.ofMillis(jo.getLong(key));
            }
        }
        {
            String key = "WriteTimeout";
            if (jo.has(key)) {
                WriteTimeout = jo.isNull(key) ? defaultWriteTimeout : Duration.ofMillis(jo.getLong(key));
            }
        }
        {
            String key = "IsQuickTest";
            if (jo.has(key)) {
                IsQuickTest = jo.isNull(key) ? defaultIsQuickTest : jo.getBoolean(key);
            }
        }
        {
            String key = "FollowRedirect";
            if (jo.has(key)) {
                FollowRedirect = jo.isNull(key) ? defaultFollowRedirect : jo.getBoolean(key);
            }
        }
        {
            String key = "AutoSendCookies";
            if (jo.has(key)) {
                AutoSendCookies = jo.isNull(key) ? null : jo.getBoolean(key);
            }
        }
        {
            String key = "AutoReceiveCookies";
            if (jo.has(key)) {
                AutoReceiveCookies = jo.isNull(key) ? null : jo.getBoolean(key);
            }
        }
        {
            String key = "Proxy";
            if (jo.has(key)) {
                Proxy = jo.isNull(key) ? null : new NetworkProxy() {{
                    JSONObject proxy = jo.getJSONObject(key);
                    {
                        String key = "Type";
                        if (proxy.has(key)) {
                            Type = proxy.isNull(key) ? null : java.net.Proxy.Type.valueOf(proxy.getString(key));
                        }
                    }
                    {
                        String key = "Host";
                        if (proxy.has(key)) {
                            Host = proxy.isNull(key) ? null : proxy.getString(key);
                        }
                    }
                    {
                        String key = "Port";
                        if (proxy.has(key)) {
                            Port = proxy.isNull(key) ? null : proxy.getInt(key);
                        }
                    }
                }};
            }
        }
        {
            String key = "StatusCode";
            if (jo.has(key)) {
                StatusCode = jo.isNull(key) ? defaultStatusCode : jo.getInt(key);
            }
        }
        {
            String key = "StatusMessage";
            if (jo.has(key)) {
                StatusMessage = jo.isNull(key) ? null : jo.getString(key);
            }
        }
        {
            String key = "ResponseHeaderList";
            if (jo.has(key)) {
                ResponseHeaderList = deserializeHeaderList(jo, key);
            }
        }
        {
            String key = "ResponseHeaderMap";
            if (jo.has(key)) {
                ResponseHeaderMap = jo.isNull(key) ? null : new HashMap<String, List<String>>() {{
                    JSONArray kvs = jo.getJSONArray(key);
                    for (int i = 0; i < kvs.length(); i++) {
                        if (kvs.isNull(i)) {
                            continue;
                        }
                        JSONArray kv = kvs.getJSONArray(i);
                        if (kv.length() < 2) {
                            continue;
                        }
                        put(
                                kv.isNull(0) ? null : kv.getString(0),
                                kv.isNull(1) ? null : new ArrayList<String>() {{
                                    JSONArray vs = kv.getJSONArray(1);
                                    for (int j = 0; j < vs.length(); j++) {
                                        add(vs.isNull(j) ? null : vs.getString(j));
                                    }
                                }}
                        );
                    }
                }};
            }
        }
        {
            String key = "responseBinary";
            if (jo.has(key)) {
                responseBinary = jo.isNull(key) ? null : Base64.getDecoder().decode(jo.getString(key));
            }
        }
        return this;
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
            this.call.updateAndGet(prev -> {
                cancelled.countDown();
                if (prev != null) {
                    prev.cancel();
                }
                return prev;
            });
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
}
