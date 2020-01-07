package tomcat.request.session;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.MessageDigest;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import org.apache.catalina.util.CustomObjectInputStream;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


public class SerializationUtil {
    private ClassLoader loader;
    private Log log = LogFactory.getLog(SerializationUtil.class);

    public SerializationUtil() {
    }

    public void setClassLoader(ClassLoader loader) {
        this.loader = loader;
    }

    public byte[] getSessionAttributesHashCode(Session session) throws IOException {
        byte[] serialized = null;
        Map<String, Object> attributes = new HashMap();
        Enumeration enumerator = session.getAttributeNames();

        while(enumerator.hasMoreElements()) {
            String key = (String)enumerator.nextElement();
            attributes.put(key, session.getAttribute(key));
        }

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        Throwable var39 = null;

        try {
            ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(bos));
            Throwable var7 = null;

            try {
                oos.writeUnshared(attributes);
                oos.flush();
                serialized = bos.toByteArray();
            } catch (Throwable var33) {
                var7 = var33;
                throw var33;
            } finally {
                if (oos != null) {
                    if (var7 != null) {
                        try {
                            oos.close();
                        } catch (Throwable var31) {
                            var7.addSuppressed(var31);
                        }
                    } else {
                        oos.close();
                    }
                }

            }
        } catch (Throwable var35) {
            var39 = var35;
            throw var35;
        } finally {
            if (bos != null) {
                if (var39 != null) {
                    try {
                        bos.close();
                    } catch (Throwable var30) {
                        var39.addSuppressed(var30);
                    }
                } else {
                    bos.close();
                }
            }

        }

        MessageDigest digester = null;

        try {
            digester = MessageDigest.getInstance("MD5");
        } catch (Exception var32) {
            this.log.error("Unable to get MessageDigest instance for MD5", var32);
        }

        return digester.digest(serialized);
    }

    public byte[] serializeSessionData(Session session, SessionMetadata metadata) throws IOException {
        byte[] serialized = null;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        Throwable var5 = null;

        try {
            ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(bos));
            Throwable var7 = null;

            try {
                oos.writeObject(metadata);
                session.writeObjectData(oos);
                oos.flush();
                serialized = bos.toByteArray();
            } catch (Throwable var30) {
                var7 = var30;
                throw var30;
            } finally {
                if (oos != null) {
                    if (var7 != null) {
                        try {
                            oos.close();
                        } catch (Throwable var29) {
                            var7.addSuppressed(var29);
                        }
                    } else {
                        oos.close();
                    }
                }

            }
        } catch (Throwable var32) {
            var5 = var32;
            throw var32;
        } finally {
            if (bos != null) {
                if (var5 != null) {
                    try {
                        bos.close();
                    } catch (Throwable var28) {
                        var5.addSuppressed(var28);
                    }
                } else {
                    bos.close();
                }
            }

        }

        return serialized;
    }

    public void deserializeSessionData(byte[] data, Session session, SessionMetadata metadata) throws IOException, ClassNotFoundException {
        BufferedInputStream bis = new BufferedInputStream(new ByteArrayInputStream(data));
        Throwable var5 = null;

        try {
            ObjectInputStream ois = new CustomObjectInputStream(bis, this.loader);
            Throwable var7 = null;

            try {
                SessionMetadata serializedMetadata = (SessionMetadata)ois.readObject();
                metadata.copyFieldsFrom(serializedMetadata);
                session.readObjectData(ois);
            } catch (Throwable var30) {
                var7 = var30;
                throw var30;
            } finally {
                if (ois != null) {
                    if (var7 != null) {
                        try {
                            ois.close();
                        } catch (Throwable var29) {
                            var7.addSuppressed(var29);
                        }
                    } else {
                        ois.close();
                    }
                }

            }
        } catch (Throwable var32) {
            var5 = var32;
            throw var32;
        } finally {
            if (bis != null) {
                if (var5 != null) {
                    try {
                        bis.close();
                    } catch (Throwable var28) {
                        var5.addSuppressed(var28);
                    }
                } else {
                    bis.close();
                }
            }

        }

    }
}
