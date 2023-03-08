package pub.telephone.javahttprequest.network.http;

import okhttp3.CookieJar;

public interface HTTPFlexibleCookieJar extends CookieJar {
    HTTPFlexibleCookieJar AsReadOnlyJar();

    HTTPFlexibleCookieJar AsWriteOnlyJar();

    HTTPFlexibleCookieJar AsReadWriteJar();
}
