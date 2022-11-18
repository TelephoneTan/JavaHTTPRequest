import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import pub.telephone.javahttprequest.network.http.HTTPCookieJar;
import pub.telephone.javahttprequest.network.http.HTTPRequest;
import pub.telephone.javahttprequest.network.http.HTTPResult;
import pub.telephone.javapromise.async.promise.PromiseFulfilledListener;
import pub.telephone.javapromise.async.promise.PromiseRejectedListener;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.List;

public class TestHTTP {
    @Test
    void test() {
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
}
