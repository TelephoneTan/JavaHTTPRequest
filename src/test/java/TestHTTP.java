import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import pub.telephone.javahttprequest.network.http.HTTPCookieJar;
import pub.telephone.javahttprequest.network.http.HTTPRequest;
import pub.telephone.javahttprequest.network.http.HTTPResult;
import pub.telephone.javapromise.async.Async;
import pub.telephone.javapromise.async.promise.PromiseFulfilledListener;
import pub.telephone.javapromise.async.promise.PromiseRejectedListener;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class TestHTTP {
    @Test
    void test() {
//        testCookieJarAndHTML();
//        testPost();
        testGBKString();
        testGBKHTML();
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
                .SetProxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", 7890)));
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
