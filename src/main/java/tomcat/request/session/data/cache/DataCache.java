package tomcat.request.session.data.cache;

public interface DataCache {
    byte[] set(String var1, byte[] var2);

    Long setnx(String var1, byte[] var2);

    Long expire(String var1, int var2);

    byte[] get(String var1);

    Long delete(String var1);
}
