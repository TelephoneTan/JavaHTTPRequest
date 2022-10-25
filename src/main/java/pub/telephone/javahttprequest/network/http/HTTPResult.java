package pub.telephone.javahttprequest.network.http;

public class HTTPResult<E> {
    public HTTPRequest Request;
    public E Result;

    public HTTPResult(HTTPRequest request, E result) {
        Request = request;
        Result = result;
    }

    public HTTPResult<E> setRequest(HTTPRequest request) {
        Request = request;
        return this;
    }

    public HTTPResult<E> setResult(E result) {
        Result = result;
        return this;
    }
}
