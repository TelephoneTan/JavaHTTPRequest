package pub.telephone.javahttprequest.network.http;

public enum HTTPHeader {
    Content_Type("Content-Type"),
    Content_Encoding("Content-Encoding"),
    Referer("Referer");

    public final String Name;

    HTTPHeader(String name) {
        Name = name;
    }
}
