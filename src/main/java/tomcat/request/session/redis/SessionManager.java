package tomcat.request.session.redis;


import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.Set;
import org.apache.catalina.Context;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Valve;
import org.apache.catalina.connector.Request;
import org.apache.catalina.session.ManagerBase;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import tomcat.request.session.SerializationUtil;
import tomcat.request.session.Session;
import tomcat.request.session.SessionConstants;
import tomcat.request.session.SessionContext;
import tomcat.request.session.SessionMetadata;
import tomcat.request.session.data.cache.DataCache;
import tomcat.request.session.data.cache.impl.RedisDataCache;

public class SessionManager extends ManagerBase implements Lifecycle {
    private DataCache dataCache;
    protected SerializationUtil serializer;
    protected ThreadLocal<SessionContext> sessionContext = new ThreadLocal();
    protected SessionHandlerValve handlerValve;
    protected Set<SessionPolicy> sessionPolicy;
    private Log log;

    public SessionManager() {
        this.sessionPolicy = EnumSet.of(SessionManager.SessionPolicy.DEFAULT);
        this.log = LogFactory.getLog(SessionManager.class);
    }

    public String getSessionPersistPolicies() {
        String policyStr = null;

        SessionManager.SessionPolicy policy;
        for(Iterator var2 = this.sessionPolicy.iterator(); var2.hasNext(); policyStr = policyStr == null ? policy.name() : policyStr.concat(",").concat(policy.name())) {
            policy = (SessionManager.SessionPolicy)var2.next();
        }

        return policyStr;
    }

    public void setSessionPersistPolicies(String policyStr) {
        Set<SessionManager.SessionPolicy> policySet = EnumSet.of(SessionManager.SessionPolicy.DEFAULT);
        String[] policyArray = policyStr.split(",");
        String[] var4 = policyArray;
        int var5 = policyArray.length;

        for(int var6 = 0; var6 < var5; ++var6) {
            String policy = var4[var6];
            policySet.add(SessionManager.SessionPolicy.fromName(policy));
        }

        this.sessionPolicy = policySet;
    }

    public boolean getSaveOnChange() {
        return this.sessionPolicy.contains(SessionManager.SessionPolicy.SAVE_ON_CHANGE);
    }

    public boolean getAlwaysSaveAfterRequest() {
        return this.sessionPolicy.contains(SessionManager.SessionPolicy.ALWAYS_SAVE_AFTER_REQUEST);
    }

    public void addLifecycleListener(LifecycleListener listener) {
        super.addLifecycleListener(listener);
    }

    public LifecycleListener[] findLifecycleListeners() {
        return super.findLifecycleListeners();
    }

    public void removeLifecycleListener(LifecycleListener listener) {
        super.removeLifecycleListener(listener);
    }

    protected synchronized void startInternal() throws LifecycleException {
        super.startInternal();
        super.setState(LifecycleState.STARTING);
        boolean initializedValve = false;
        Context context = this.getContextIns();
        Valve[] var3 = context.getPipeline().getValves();
        int var4 = var3.length;

        for(int var5 = 0; var5 < var4; ++var5) {
            Valve valve = var3[var5];
            if (valve instanceof SessionHandlerValve) {
                this.handlerValve = (SessionHandlerValve)valve;
                this.handlerValve.setSessionManager(this);
                initializedValve = true;
                break;
            }
        }

        if (!initializedValve) {
            throw new LifecycleException("Session handling valve is not initialized..");
        } else {
            this.initialize();
            this.log.info("The sessions will expire after " + this.getSessionTimeout((Session)null) + " seconds.");
            context.setDistributable(true);
        }
    }

    protected synchronized void stopInternal() throws LifecycleException {
        super.setState(LifecycleState.STOPPING);
        super.stopInternal();
    }

    public Session createSession(String sessionId) {
        this.log.info("createSession sessionId " + sessionId);
        if (sessionId != null) {
            sessionId = this.dataCache.setnx(sessionId, SessionConstants.NULL_SESSION) == 0L ? null : sessionId;
            this.log.info("createSession sessionId!=null " + sessionId);
        } else {
            do {
                sessionId = this.generateSessionId();
            } while(this.dataCache.setnx(sessionId, SessionConstants.NULL_SESSION) == 0L);

            this.log.info("createSession sessionId==null do " + sessionId);
        }

        Session session = sessionId != null ? this.createEmptySession() : null;
        if (session != null) {
            session.setId(sessionId);
            session.setNew(true);
            session.setValid(true);
            session.setCreationTime(System.currentTimeMillis());
            session.setMaxInactiveInterval(this.getSessionTimeout(session));
            session.tellNew();
            this.log.info("createSession if (session != null) " + sessionId);
        }

        this.setValues(sessionId, session, false, new SessionMetadata());
        this.log.info("createSession after setValues " + sessionId);
        if (session != null) {
            try {
                this.save(session, true);
                this.log.info("createSession after save " + sessionId);
            } catch (Exception var4) {
                this.log.error("Error occured while creating session..", var4);
                this.setValues((String)null, (Session)null);
                session = null;
            }
        }

        return session;
    }

    public Session createEmptySession() {
        return new Session(this);
    }

    public void add(org.apache.catalina.Session session) {
        this.save(session, false);
    }

    public Session findSession(String sessionId) throws IOException {
        this.log.info("findSession sessionId " + sessionId);
        Session session = null;
        if (sessionId != null && this.sessionContext.get() != null && sessionId.equals(((SessionContext)this.sessionContext.get()).getId())) {
            session = ((SessionContext)this.sessionContext.get()).getSession();
            this.log.info("findSession sessionId!=null " + session);
        } else {
            byte[] data = this.dataCache.get(sessionId);
            this.log.info("findSession this.dataCache.get " + data);
            boolean isPersisted = false;
            SessionMetadata metadata = null;
            if (data == null) {
                session = null;
                metadata = null;
                sessionId = null;
                isPersisted = false;
            } else {
                if (Arrays.equals(SessionConstants.NULL_SESSION, data)) {
                    throw new IOException("NULL session data");
                }

                try {
                    metadata = new SessionMetadata();
                    Session newSession = this.createEmptySession();
                    this.serializer.deserializeSessionData(data, newSession, metadata);
                    newSession.setId(sessionId);
                    newSession.access();
                    newSession.setNew(false);
                    newSession.setValid(true);
                    newSession.resetDirtyTracking();
                    newSession.setMaxInactiveInterval(this.getSessionTimeout(newSession));
                    session = newSession;
                    isPersisted = true;
                } catch (Exception var7) {
                    this.log.error("Error occured while de-serializing the session object..", var7);
                }
            }

            this.setValues(sessionId, session, isPersisted, metadata);
        }

        return session;
    }

    public void remove(org.apache.catalina.Session session) {
        this.remove(session, false);
    }

    public void remove(org.apache.catalina.Session session, boolean update) {
        this.dataCache.expire(session.getId(), 0);
        //this.dataCache.expire(session.getId(), 10);
    }

    public void load() throws ClassNotFoundException, IOException {
    }

    public void unload() throws IOException {
    }

    private void initialize() {
        try {
            this.dataCache = new RedisDataCache();
            this.serializer = new SerializationUtil();
            Context context = this.getContextIns();
            ClassLoader loader = context != null && context.getLoader() != null ? context.getLoader().getClassLoader() : null;
            this.serializer.setClassLoader(loader);
        } catch (Exception var3) {
            this.log.error("Error occured while initializing the session manager..", var3);
            throw var3;
        }
    }

    public void save(org.apache.catalina.Session session, boolean forceSave) {
        try {
            Session newSession;
            label31: {
                newSession = (Session)session;
                byte[] hash = this.sessionContext.get() != null && ((SessionContext)this.sessionContext.get()).getMetadata() != null ? ((SessionContext)this.sessionContext.get()).getMetadata().getAttributesHash() : null;
                byte[] currentHash = this.serializer.getSessionAttributesHashCode(newSession);
                if (!forceSave && !newSession.isDirty()) {
                    Boolean var10000 = this.sessionContext.get() != null ? ((SessionContext)this.sessionContext.get()).isPersisted() : null;
                    Boolean isPersisted = var10000;
                    if (var10000 != null && isPersisted && Arrays.equals(hash, currentHash)) {
                        break label31;
                    }
                }

                SessionMetadata metadata = new SessionMetadata();
                metadata.setAttributesHash(currentHash);
                this.log.info("save before this.dataCache.set" + newSession.getId());
                this.dataCache.set(newSession.getId(), this.serializer.serializeSessionData(newSession, metadata));
                this.log.info("save after this.dataCache.set" + newSession.getId());
                newSession.resetDirtyTracking();
                this.setValues(true, metadata);
            }

            int timeout = this.getSessionTimeout(newSession);
            this.dataCache.expire(newSession.getId(), timeout);
            this.log.info("save after timeout " + timeout);
            this.log.trace("Session [" + newSession.getId() + "] expire in [" + timeout + "] seconds.");
        } catch (IOException var8) {
            this.log.error("Error occured while saving the session object in data cache..", var8);
        }

    }

    public void afterRequest(Request request) {
        this.log.info("afterRequest 0" + request.getRequestURL());
        Session session = null;

        try {
            session = this.sessionContext.get() != null ? ((SessionContext)this.sessionContext.get()).getSession() : null;
            if (session != null) {
                if (session.isValid()) {
                    this.save(session, this.getAlwaysSaveAfterRequest());
                } else {
                    this.remove(session);
                }

                this.log.info("Session object " + (session.isValid() ? "saved: " : "removed: ") + session.getId());
            }else{
                this.log.info("afterRequest session==null");
            }
        } catch (Exception var7) {
            this.log.error("Error occured while processing post request process..", var7);
        } finally {
            this.sessionContext.remove();
            this.log.info("Session removed from ThreadLocal:" + (session != null ? session.getIdInternal() : ""));
        }

    }

    private int getSessionTimeout(Session session) {
        int timeout = this.getContextIns().getSessionTimeout() * 60;
        int sessionTimeout = session == null ? 0 : session.getMaxInactiveInterval();
        return sessionTimeout < timeout ? (timeout < 1800 ? 1800 : timeout) : sessionTimeout;
    }

    private void setValues(String sessionId, Session session) {
        if (this.sessionContext.get() == null) {
            this.sessionContext.set(new SessionContext());
        }

        ((SessionContext)this.sessionContext.get()).setId(sessionId);
        ((SessionContext)this.sessionContext.get()).setSession(session);
    }

    private void setValues(boolean isPersisted, SessionMetadata metadata) {
        if (this.sessionContext.get() == null) {
            this.sessionContext.set(new SessionContext());
        }

        ((SessionContext)this.sessionContext.get()).setMetadata(metadata);
        ((SessionContext)this.sessionContext.get()).setPersisted(isPersisted);
    }

    private void setValues(String sessionId, Session session, boolean isPersisted, SessionMetadata metadata) {
        this.setValues(sessionId, session);
        this.setValues(isPersisted, metadata);
    }

    private Context getContextIns() {
        try {
            Method method = this.getClass().getSuperclass().getDeclaredMethod("getContext");
            return (Context)method.invoke(this);
        } catch (Exception var4) {
            try {
                Method method = this.getClass().getSuperclass().getDeclaredMethod("getContainer");
                return (Context)method.invoke(this);
            } catch (Exception var3) {
                this.log.error("Error in getContext", var3);
                return null;
            }
        }
    }

    static enum SessionPolicy {
        DEFAULT,
        SAVE_ON_CHANGE,
        ALWAYS_SAVE_AFTER_REQUEST;

        private SessionPolicy() {
        }

        static SessionManager.SessionPolicy fromName(String name) {
            SessionManager.SessionPolicy[] var1 = values();
            int var2 = var1.length;

            for(int var3 = 0; var3 < var2; ++var3) {
                SessionManager.SessionPolicy policy = var1[var3];
                if (policy.name().equalsIgnoreCase(name)) {
                    return policy;
                }
            }

            throw new IllegalArgumentException("Invalid session policy [" + name + "]");
        }
    }
}
