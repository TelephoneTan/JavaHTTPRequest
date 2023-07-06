package pub.telephone.javahttprequest.util;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class Util {
    public static boolean EqualsIgnoreCase(String s, String s2) {
        return Objects.equals(s, s2) || (s != null && s.equalsIgnoreCase(s2));
    }

    public static <V> void MapSet(Map<String, V> map, String key, V value) {
        if (map != null) {
            for (String s : map.keySet()) {
                if (EqualsIgnoreCase(s, key)) {
                    key = s;
                    break;
                }
            }
            map.put(key, value);
        }
    }

    public static <V> void MapRemove(Map<String, V> map, String key) {
        if (map != null) {
            for (String s : map.keySet()) {
                if (EqualsIgnoreCase(s, key)) {
                    key = s;
                    break;
                }
            }
            map.remove(key);
        }
    }

    public static <V> V MapGet(Map<String, V> map, String key) {
        if (map == null) {
            return null;
        }
        for (String s : map.keySet()) {
            if (EqualsIgnoreCase(s, key)) {
                key = s;
                break;
            }
        }
        return map.get(key);
    }

    public static String GetEmptyStringFromNull(String s) {
        return s == null ? "" : s;
    }

    public static boolean IsNullOrEmpty(String s) {
        return s == null || s.isEmpty();
    }

    public static boolean NotEmpty(String s) {
        return !IsNullOrEmpty(s);
    }

    public static boolean WaitLatch(CountDownLatch latch, CountDownLatch... quitLatch) {
        try {
            latch.await();
            return quitLatch == null || quitLatch.length == 0 || quitLatch[0].await(0, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
            return true;
        }
    }

    public static boolean TryWaitLatch(CountDownLatch latch) {
        try {
            return latch.await(0, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static void NotifyQuit(CountDownLatch quitLatch, CountDownLatch... otherLatchList) {
        quitLatch.countDown();
        if (otherLatchList != null) {
            for (CountDownLatch latch : otherLatchList) {
                latch.countDown();
            }
        }
    }

    public static <E> int IncreaseIndex(E[] array, int index) {
        if (array == null || array.length == 0) {
            return index;
        }
        index++;
        if (index >= array.length) {
            index = 0;
        }
        return index;
    }

    public static void Transfer(InputStream inputStream, OutputStream outputStream) throws IOException {
        try (InputStream is = inputStream; OutputStream os = outputStream) {
            int len;
            byte[] buf = new byte[1024 * 16];
            while (true) {
                try {
                    len = is.read(buf);
                } catch (EOFException ignored) {
                    len = -1;
                }
                if (len > 0) {
                    os.write(buf, 0, len);
                } else {
                    return;
                }
            }
        }
    }
}
