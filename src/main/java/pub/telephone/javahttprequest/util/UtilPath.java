package pub.telephone.javahttprequest.util;

public class UtilPath {
    public static String RemovePathSeparatorSuffix(String s) {
        s = Util.GetEmptyStringFromNull(s);
        StringBuilder sb = new StringBuilder(s);
        while (sb.codePointAt(sb.length() - 1) == '/') {
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb.length() == 0 ? "." : sb.toString();
    }

    public static String PrependPathSeparator(String s) {
        s = Util.GetEmptyStringFromNull(s);
        if (!s.startsWith("/")) {
            s = "/" + s;
        }
        return s;
    }
}
