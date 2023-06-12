package pub.telephone.javahttprequest.network.http;

import okhttp3.CookieJar;

public interface HTTPFlexibleCookieJar extends CookieJar {
    HTTPFlexibleCookieJar AsReadOnlyJar();

    HTTPFlexibleCookieJar AsWriteOnlyJar();

    HTTPFlexibleCookieJar AsReadWriteJar();

    HTTPFlexibleCookieJar AsNoJar();

    HTTPFlexibleCookieJar WithRead(boolean readable);

    HTTPFlexibleCookieJar WithWrite(boolean writable);

    HTTPFlexibleCookieJar WithReadWrite(boolean readable, boolean writable);

    void SetCookies(String[][] urlCookieList);
}
