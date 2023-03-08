package pub.telephone.javahttprequest.network.http;

import okhttp3.Cookie;
import okhttp3.HttpUrl;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class HTTPCookieJar implements HTTPFlexibleCookieJar {
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

    final Map<cookieKey, Cookie> map;

    public HTTPCookieJar() {
        this.map = new HashMap<>();
    }

    public HTTPCookieJar(HTTPCookieJar jar) {
        this.map = jar.map;
    }

    @NotNull
    @Override
    public List<Cookie> loadForRequest(@NotNull HttpUrl httpUrl) {
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
            matchList.sort((o1, o2) -> o2.path().length() - o1.path().length());
            return matchList;
        }
    }

    @Override
    public void saveFromResponse(@NotNull HttpUrl httpUrl, @NotNull List<Cookie> list) {
        synchronized (map) {
            for (Cookie cookie : list) {
                if (cookie != null) {
                    map.put(new cookieKey(cookie), cookie);
                }
            }
        }
    }

    static class httpReadOnlyCookieJar extends HTTPCookieJar {
        public httpReadOnlyCookieJar(HTTPCookieJar jar) {
            super(jar);
        }

        @Override
        public void saveFromResponse(@NotNull HttpUrl httpUrl, @NotNull List<Cookie> list) {
        }
    }

    static class httpWriteOnlyCookieJar extends HTTPCookieJar {
        public httpWriteOnlyCookieJar(HTTPCookieJar jar) {
            super(jar);
        }

        @NotNull
        @Override
        public List<Cookie> loadForRequest(@NotNull HttpUrl httpUrl) {
            return new LinkedList<>();
        }
    }

    public HTTPCookieJar AsReadOnlyJar() {
        return this instanceof httpReadOnlyCookieJar ? this : new httpReadOnlyCookieJar(this);
    }

    public HTTPCookieJar AsWriteOnlyJar() {
        return this instanceof httpWriteOnlyCookieJar ? this : new httpWriteOnlyCookieJar(this);
    }

    public HTTPCookieJar AsReadWriteJar() {
        return this instanceof httpReadOnlyCookieJar || this instanceof httpWriteOnlyCookieJar ?
                new HTTPCookieJar(this) : this;
    }
}
