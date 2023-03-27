package pub.telephone.javahttprequest.network;

import java.net.InetSocketAddress;
import java.net.Proxy;

public class NetworkProxy {
    public Proxy.Type Type;
    public String Host;
    public Integer Port;

    public NetworkProxy(Proxy.Type type, String host, Integer port) {
        Type = type;
        Host = host;
        Port = port;
    }

    public NetworkProxy() {
    }

    public Proxy Proxy() {
        return Type == Proxy.Type.DIRECT || Host == null || Port == null ?
                Proxy.NO_PROXY :
                new Proxy(Type, new InetSocketAddress(Host, Port));
    }
}
