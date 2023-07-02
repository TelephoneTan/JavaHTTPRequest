import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import pub.telephone.javahttprequest.network.NetworkProxy;
import pub.telephone.javahttprequest.network.http.HTTPCookieJar;
import pub.telephone.javahttprequest.network.http.HTTPMethod;
import pub.telephone.javahttprequest.network.http.HTTPRequest;
import pub.telephone.javahttprequest.network.http.HTTPResult;
import pub.telephone.javapromise.async.Async;
import pub.telephone.javapromise.async.promise.Promise;
import pub.telephone.javapromise.async.promise.PromiseFulfilledListener;
import pub.telephone.javapromise.async.promise.PromiseRejectedListener;
import pub.telephone.javapromise.async.promise.PromiseSemaphore;
import pub.telephone.javapromise.async.task.timed.TimedTask;

import java.io.File;
import java.net.Proxy;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TestHTTP {
    @Test
    void test() {
        testCancel();
//        testProxy();
//        testSerialize();
//        testGet();
//        testSemaphore();
//        testCookieJarAndHTML();
//        testPost();
//        testGBKString();
//        testGBKHTML();
    }

    void testCancel() {
        HTTPRequest req = new HTTPRequest(HTTPMethod.GET, "long request url here")
                .SetProxy(new NetworkProxy(Proxy.Type.HTTP, "localhost", 7892))
                .SetCustomizedHeaderList(Arrays.asList(
                        new String[]{"***", "***"},
                        new String[]{"***", "***"}
                ));
        req.String()
                .ForCancel(() -> System.out.println("外部已取消"))
                .Finally(() -> {
                    System.out.println("请求已结束");
                    return null;
                });
        new TimedTask(Duration.ZERO, (resolver, rejector) -> {
            req.Cancel();
            resolver.Resolve(false);
        }).Start(Duration.ofSeconds(5));
        Async.Delay(Duration.ofSeconds(25)).Await();
    }

    void testProxy() {
        HTTPRequest request = new HTTPRequest(HTTPMethod.POST, "https://www.baidu.com");
        request
                .SetProxy(new NetworkProxy(Proxy.Type.SOCKS, "localhost", 7893))
                .SetRequestForm(new ArrayList<String[]>() {{
                    add(new String[]{"xxx", "yy"});
                    add(new String[]{"aaa", "bb"});
                }})
                .SetCustomizedHeaderList(new ArrayList<String[]>() {{
                    add(new String[]{"with", "me"});
                    add(new String[]{"and", "you!"});
                }})
                .String()
                .Then(strRes -> {
                    System.out.println(strRes.Result);
                    return null;
                })
                .Catch(throwable -> {
                    throwable.printStackTrace();
                    return null;
                })
                .Await();
    }

    void testSerialize() {
        HTTPRequest request = new HTTPRequest(HTTPMethod.POST, "https://google.com");
        request
                .SetProxy(new NetworkProxy(Proxy.Type.SOCKS, "localhost", 7890))
                .SetRequestForm(new ArrayList<String[]>() {{
                    add(new String[]{"xx", "yy"});
                    add(new String[]{"aa", "bb"});
                }})
                .SetCustomizedHeaderList(new ArrayList<String[]>() {{
                    add(new String[]{"with", "me"});
                    add(new String[]{"and", "you!"});
                }})
                .Deserialize(request.Serialize())
                .String()
                .Then(strRes -> {
                    System.out.println(strRes.Request.Deserialize(strRes.Request.Serialize()).Serialize());
                    return null;
                })
                .Catch(throwable -> {
                    throwable.printStackTrace();
                    return null;
                })
                .Await();
    }

    void testGet() {
        HTTPRequest request = new HTTPRequest("https://google.com")
                .SetProxy(new NetworkProxy(Proxy.Type.HTTP, "localhost", 7890));
        request.Deserialize(request.Serialize())
                .String()
                .Then(strRes -> {
                    System.out.println(strRes.Result);
                    return null;
                })
                .Catch(throwable -> {
                    throwable.printStackTrace();
                    return null;
                }).Await();
    }

    void testSemaphore() {
        long sts = System.currentTimeMillis();
        HTTPRequest request = new HTTPRequest("https://www.baidu.com", new PromiseSemaphore(1));
        List<Promise<Object>> all = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            all.add(request.clone().String().Then(v -> {
//                System.out.println(v.Request.StatusCode);
                return null;
            }));
        }
        Promise.AwaitAll(all);
        long ets = System.currentTimeMillis();
        System.out.printf("用时 %dms\n", ets - sts);
    }

    void testPost() {
        long sts = System.currentTimeMillis();
        HTTPRequest request = HTTPRequest.Post("http://localhost:3000");
        Async.Delay(Duration.ofSeconds(30)).Then(value -> {
            request.Cancel();
            return null;
        });
        request
                .SetConnectTimeout(Duration.ofHours(1))
                .SetWriteTimeout(Duration.ofHours(1))
                .SetReadTimeout(Duration.ofHours(1))
                .SetCustomizedHeaderList(new ArrayList<String[]>() {
                    {
                        add(new String[]{"Accept-Encoding", "identity"});
                    }
                })
//                .SetRequestString("hello, world")
                .SetRequestFile(new File("Maroon 5 - Sugar.mp3"))
                .File(new File("蜜糖.mp3"))
//                .SetRequestFile(new File("Maroon 5 - Sugar.mp4"))
//                .File(new File("蜜糖.mp4"))
//                .SetRequestFile(new File("Win10_21H2_Chinese(Simplified)_x64.iso"))
//                .File(new File("Win10_21H2_Chinese(Simplified)_x64.iso"))
//                .SetRequestFile(new File("macOS Monterey by Techrechard.com.iso"))
//                .File(new File("macOS Monterey by Techrechard.com.iso"))
                .Then(value -> {
                    System.out.println("---------------------");
                    System.out.println(Files.size(value.Result.toPath()));
                    System.out.println("#####################");
                    List<String[]> header = value.Request.ResponseHeaderList;
                    if (header != null) {
                        for (String[] h : header) {
                            System.out.println(h[0] + ": " + h[1]);
                        }
                    }
                    return null;
                })
                .Catch(reason -> {
                    reason.printStackTrace();
                    return null;
                })
                .ForCancel(() -> System.out.println("被取消了"))
                .Finally(() -> {
                    long ets = System.currentTimeMillis();
                    System.out.printf("结束了 %ds\n", (ets - sts) / 1000);
                    return null;
                }).Await();
    }

    void testCookieJarAndHTML() {
        HTTPRequest request = new HTTPRequest("https://v.guet.edu.cn")
                .SetProxy(new NetworkProxy(Proxy.Type.HTTP, "127.0.0.1", 7890));
        PromiseFulfilledListener<HTTPResult<Document>, Object> then = documentResult -> {
            System.out.println(documentResult.Result.toString());
            return documentResult.Request.Send()
                    .Then((PromiseFulfilledListener<HTTPResult<HTTPRequest>, HTTPResult<Document>>) requestResult ->
                            requestResult.Result.HTMLDocument(requestResult.Result.URL)
                    )
                    .Then(urlDocResult -> {
                        Thread.sleep(3000);
                        System.out.println();
                        System.out.println("=====================");
                        System.out.println();
                        System.out.println(urlDocResult.Result.toString());
                        System.out.println("#####################");
                        List<String[]> header = urlDocResult.Request.ResponseHeaderList;
                        if (header != null) {
                            for (String[] h : header) {
                                System.out.println(h[0] + ": " + h[1]);
                            }
                        }
                        return null;
                    });
        };
        PromiseRejectedListener<Object> cat = reason -> {
            System.out.println("----------------");
            System.out.println(reason.toString());
            return null;
        };
        HTTPCookieJar jar = new HTTPCookieJar();
        jar = jar.AsReadOnlyJar();
        request.clone()
                .SetCookieJar(jar)
                .HTMLDocument().Then(then).Catch(cat).Await();
        jar = jar.AsWriteOnlyJar();
        request.clone()
                .SetCookieJar(jar)
                .HTMLDocument().Then(then).Catch(cat).Await();
        jar = jar.AsReadWriteJar();
        request.clone()
                .SetCookieJar(jar)
                .HTMLDocument().Then(then).Catch(cat).Await();
    }

    void testGBKString() {
        String s = "你好，世界";
        Charset gbk = Charset.forName("GBK");
        HTTPRequest.Post("http://localhost:3000").SetRequestBinary(s.getBytes(gbk)).String().Then(stringHTTPResult -> {
            System.out.println(stringHTTPResult.Result);
            return null;
        }).Await();
        HTTPRequest.Post("http://localhost:3000").SetRequestBinary(s.getBytes(gbk)).String(gbk).Then(stringHTTPResult -> {
            System.out.println(stringHTTPResult.Result);
            return null;
        }).Await();
    }

    void testGBKHTML() {
        String s = "<!DOCTYPE html>\n" +
                "<html lang=\"en\">\n" +
                "  <head>\n" +
                "    <meta charset=\"utf-8\">\n" +
                "    <title>title</title>\n" +
                "    <link rel=\"stylesheet\" href=\"style.css\">\n" +
                "    <script src=\"script.js\"></script>\n" +
                "  </head>\n" +
                "  <body>\n" +
                "    你好，世界\n" +
                "  </body>\n" +
                "</html>";
        Charset gbk = Charset.forName("GBK");
        HTTPRequest.Post("http://localhost:3000").SetRequestBinary(s.getBytes(gbk)).HTMLDocument().Then(value -> {
            System.out.println(value.Result.toString());
            return null;
        }).Await();
        HTTPRequest.Post("http://localhost:3000").SetRequestBinary(s.getBytes(gbk)).HTMLDocument(gbk).Then(value -> {
            System.out.println(value.Result.toString());
            return null;
        }).Await();
    }
}
