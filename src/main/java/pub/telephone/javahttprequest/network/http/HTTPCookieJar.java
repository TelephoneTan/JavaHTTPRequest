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

    HTTPCookieJar(HTTPCookieJar jar, Boolean readable, Boolean writable) {
        this.map = jar == null ? new HashMap<>() : jar.map;
        this.readable = readable == null || readable;
        this.writable = writable == null || writable;
    }

    public HTTPCookieJar() {
        this(null, null, null);
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

    HTTPCookieJar require(boolean r, boolean w) {
        return r == this.readable && w == this.writable ?
                this :
                new HTTPCookieJar(this, r, w);
    }

    @Override
    public HTTPCookieJar AsReadOnlyJar() {
        return require(true, false);
    }

    @Override
    public HTTPCookieJar AsWriteOnlyJar() {
        return require(false, true);
    }

    @Override
    public HTTPCookieJar AsReadWriteJar() {
        return require(true, true);
    }

    @Override
    public HTTPFlexibleCookieJar AsNoJar() {
        return require(false, false);
    }

    @Override
    public HTTPFlexibleCookieJar WithRead(boolean readable) {
        return require(readable, writable);
    }

    @Override
    public HTTPFlexibleCookieJar WithWrite(boolean writable) {
        return require(readable, writable);
    }

    @Override
    public HTTPFlexibleCookieJar WithReadWrite(boolean readable, boolean writable) {
        return require(readable, writable);
    }

    @Override
    public void SetCookies(String[][] urlCookieList) {
        if (urlCookieList == null) {
            return;
        }
        Map<String, List<Cookie>> toSet = new HashMap<>();
        for (String[] urlCookie : urlCookieList) {
            String url = null;
            String cookie = null;
            if (urlCookie != null && urlCookie.length > 0) {
                url = urlCookie[0];
                if (urlCookie.length > 1) {
                    cookie = urlCookie[1];
                }
            }
            HttpUrl u = HttpUrl.parse(Util.GetEmptyStringFromNull(url));
            if (u != null) {
                Cookie c = Cookie.parse(u, Util.GetEmptyStringFromNull(cookie));
                if (c != null) {
                    if (!toSet.containsKey(url)) {
                        toSet.put(url, new ArrayList<>());
                    }
                    toSet.get(url).add(c);
                }
            }
        }
        for (String url : toSet.keySet()) {
            HttpUrl u = HttpUrl.parse(Util.GetEmptyStringFromNull(url));
            if (u != null) {
                saveFromResponse(u, toSet.get(url));
            }
        }
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
