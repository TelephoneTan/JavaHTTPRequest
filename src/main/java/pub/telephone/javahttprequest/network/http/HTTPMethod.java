package pub.telephone.javahttprequest.network.http;

public enum HTTPMethod {
    GET("GET"),
    POST("POST"),
    PUT("PUT"),
    DELETE("DELETE"),
    HEAD("HEAD"),
    OPTIONS("OPTIONS"),
    TRACE("TRACE"),
    PATCH("PATCH");

    public final String Name;

    HTTPMethod(String name) {
        Name = name;
    }
}
