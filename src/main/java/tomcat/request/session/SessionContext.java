package tomcat.request.session;

public class SessionContext {
    private String id;
    private Session session;
    private boolean persisted;
    private SessionMetadata metadata;

    public SessionContext() {
    }

    public String getId() {
        return this.id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Session getSession() {
        return this.session;
    }

    public void setSession(Session session) {
        this.session = session;
    }

    public boolean isPersisted() {
        return this.persisted;
    }

    public void setPersisted(boolean persisted) {
        this.persisted = persisted;
    }

    public SessionMetadata getMetadata() {
        return this.metadata;
    }

    public void setMetadata(SessionMetadata metadata) {
        this.metadata = metadata;
    }

    public String toString() {
        return "SessionContext [id=" + this.id + "]";
    }
}
