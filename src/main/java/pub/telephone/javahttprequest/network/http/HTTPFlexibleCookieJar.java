package pub.telephone.javahttprequest.network.http;

import okhttp3.CookieJar;

import java.util.List;
import java.util.Map;

public interface HTTPFlexibleCookieJar extends CookieJar {
    HTTPFlexibleCookieJar AsReadOnlyJar();

    HTTPFlexibleCookieJar AsWriteOnlyJar();

    HTTPFlexibleCookieJar AsReadWriteJar();

    HTTPFlexibleCookieJar AsNoJar();

    HTTPFlexibleCookieJar WithRead(boolean readable);

    HTTPFlexibleCookieJar WithWrite(boolean writable);

    HTTPFlexibleCookieJar WithReadWrite(boolean readable, boolean writable);

    HTTPFlexibleCookieJar WithCustomRequestCookies(List<Map.Entry<String, String>> urlSetCookieList);
}
