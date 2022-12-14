package pub.telephone.javahttprequest.network.mime;

public enum MIMEType {
    XWWWFormURLEncoded("application/x-www-form-urlencoded"),
    TextPlain("text/plain"),
    ApplicationOctetStream("application/octet-stream");

    public final String Name;

    MIMEType(String name) {
        Name = name;
    }
}
