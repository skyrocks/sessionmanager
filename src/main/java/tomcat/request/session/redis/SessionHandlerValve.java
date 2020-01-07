package tomcat.request.session.redis;

import java.io.IOException;
import javax.servlet.ServletException;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;

public class SessionHandlerValve extends ValveBase {
    private SessionManager manager;

    public SessionHandlerValve() {
    }

    public void setSessionManager(SessionManager manager) {
        this.manager = manager;
    }

    public void invoke(Request request, Response response) throws IOException, ServletException {
        try {
            this.getNext().invoke(request, response);
        } finally {
            this.manager.afterRequest(request);
        }

    }
}
