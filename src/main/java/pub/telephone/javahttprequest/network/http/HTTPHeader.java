package pub.telephone.javahttprequest.network.http;

public enum HTTPHeader {
    ContentType("Content-Type"),
    ContentEncoding("Content-Encoding"),
    Referer("Referer");

    public final String Name;

    HTTPHeader(String name) {
        Name = name;
    }
}
