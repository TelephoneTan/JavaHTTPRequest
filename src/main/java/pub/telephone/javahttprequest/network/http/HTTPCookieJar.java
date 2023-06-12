package pub.telephone.javahttprequest.network.http;

import okhttp3.Cookie;
import okhttp3.HttpUrl;
import org.jetbrains.annotations.NotNull;
import pub.telephone.javahttprequest.util.Util;

import java.util.*;

public class HTTPCookieJar implements HTTPFlexibleCookieJar {
    final Map<cookieKey, Cookie> map;
    final boolean readable;
    final boolean writable;
    final List<String[]> customCookieList;

    HTTPCookieJar(HTTPCookieJar jar, Boolean readable, Boolean writable, List<String[]> customCookieList) {
        this.map = jar == null ? new HashMap<>() : jar.map;
        this.readable = readable == null || readable;
        this.writable = writable == null || writable;
        this.customCookieList = customCookieList;
    }

    public HTTPCookieJar() {
        this(null, null, null, null);
    }

    @NotNull
    @Override
    public List<Cookie> loadForRequest(@NotNull HttpUrl httpUrl) {
        if (!readable) {
            return new ArrayList<>();
        }
        synchronized (map) {
            List<Cookie> matchList = new LinkedList<>();
            long nts = System.currentTimeMillis();
            Iterator<Map.Entry<cookieKey, Cookie>> it = map.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<cookieKey, Cookie> entry = it.next();
                Cookie cookie = entry.getValue();
                if (cookie == null || cookie.expiresAt() < nts) {
                    it.remove();
                } else if (cookie.matches(httpUrl)) {
                    matchList.add(cookie);
                }
            }
            if (customCookieList != null) {
                for (String[] cc : customCookieList) {
                    String k = null;
                    String v = null;
                    if (cc != null && cc.length > 0) {
                        k = cc[0];
                        if (cc.length > 1) {
                            v = cc[1];
                        }
                    }
                    HttpUrl u = HttpUrl.parse(Util.GetEmptyStringFromNull(k));
                    if (u != null) {
                        Cookie c = Cookie.parse(u, Util.GetEmptyStringFromNull(v));
                        if (c != null) {
                            matchList.add(c);
                        }
                    }
                }
            }
            matchList.sort((o1, o2) -> o2.path().length() - o1.path().length());
            return matchList;
        }
    }

    @Override
    public void saveFromResponse(@NotNull HttpUrl httpUrl, @NotNull List<Cookie> list) {
        if (!writable) {
            return;
        }
        synchronized (map) {
            for (Cookie cookie : list) {
                if (cookie != null) {
                    map.put(new cookieKey(cookie), cookie);
                }
            }
        }
    }

    HTTPCookieJar require(boolean r, boolean w, List<String[]> customCookieList) {
        return r == this.readable && w == this.writable && customCookieList == this.customCookieList ?
                this :
                new HTTPCookieJar(this, r, w, customCookieList);
    }

    @Override
    public HTTPCookieJar AsReadOnlyJar() {
        return require(true, false, customCookieList);
    }

    @Override
    public HTTPCookieJar AsWriteOnlyJar() {
        return require(false, true, customCookieList);
    }

    @Override
    public HTTPCookieJar AsReadWriteJar() {
        return require(true, true, customCookieList);
    }

    @Override
    public HTTPFlexibleCookieJar AsNoJar() {
        return require(false, false, customCookieList);
    }

    @Override
    public HTTPFlexibleCookieJar WithRead(boolean readable) {
        return require(readable, writable, customCookieList);
    }

    @Override
    public HTTPFlexibleCookieJar WithWrite(boolean writable) {
        return require(readable, writable, customCookieList);
    }

    @Override
    public HTTPFlexibleCookieJar WithReadWrite(boolean readable, boolean writable) {
        return require(readable, writable, customCookieList);
    }

    @Override
    public HTTPFlexibleCookieJar WithCustomRequestCookies(List<String[]> urlSetCookieList) {
        return require(readable, writable, urlSetCookieList);
    }

    static class cookieKey {
        final String name;
        final String domain;
        final String path;
        final boolean secure;
        final boolean httpOnly;
        final boolean persistent;
        final boolean hostOnly;

        cookieKey(Cookie cookie) {
            this.name = cookie.name();
            this.domain = cookie.domain();
            this.path = cookie.path();
            this.secure = cookie.secure();
            this.httpOnly = cookie.httpOnly();
            this.persistent = cookie.persistent();
            this.hostOnly = cookie.hostOnly();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof cookieKey)) return false;
            cookieKey cookieKey = (cookieKey) o;
            return secure == cookieKey.secure && httpOnly == cookieKey.httpOnly && persistent == cookieKey.persistent && hostOnly == cookieKey.hostOnly && name.equals(cookieKey.name) && domain.equals(cookieKey.domain) && path.equals(cookieKey.path);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, domain, path, secure, httpOnly, persistent, hostOnly);
        }
    }
}
