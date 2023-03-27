package pub.telephone.javahttprequest.network.mime;

public enum MIMEType {
    ImageJPEG("image/jpeg"),
    ImagePNG("image/png"),
    ApplicationJSON("application/json"),
    XWWWFormURLEncoded("application/x-www-form-urlencoded"),
    TextPlainUTF8("text/html;charset=utf-8"),
    ApplicationOctetStream("application/octet-stream");

    public final String Name;

    MIMEType(String name) {
        Name = name;
    }
}
