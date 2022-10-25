package pub.telephone.javahttprequest.network.mime;

public enum MIMEType {
    X_WWW_Form_URLEncoded("application/x-www-form-urlencoded");

    public final String Name;

    MIMEType(String name) {
        Name = name;
    }
}
