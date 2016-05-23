package io.github.bckfnn.actioner;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.Session;
import io.vertx.ext.web.handler.impl.UserHolder;
import io.vertx.ext.web.sstore.impl.LocalSessionStoreImpl;
import io.vertx.ext.web.sstore.impl.SessionImpl;

public class PersistentLocalSessionStore extends LocalSessionStoreImpl {
    private static Logger log = LoggerFactory.getLogger(PersistentLocalSessionStore.class);
    
    private String sessionStorage;
    
    public PersistentLocalSessionStore(Vertx vertx, String sessionMapName, long reaperInterval, String sessionStorage) {
        super(vertx, sessionMapName, reaperInterval);
        this.sessionStorage = sessionStorage;
        try {
            try (InputStream is = new FileInputStream(sessionStorage)) {
                Buffer buf = Buffer.buffer(Utils.readAsBytes(is));
                for (int pos = 0; pos < buf.length(); ) {
                    SessionImpl session = new SessionImpl();
                    pos = session.readFromBuffer(pos, buf);
                    session.setAccessed();
                    localMap.put(session.id(), session);
                }
            }
        } catch (Exception e) {
            log.info("failed to load local sessions " + e.getMessage());
        }
    }

    @Override
    public synchronized void close() {
        super.close();
        Buffer buf = Buffer.buffer();
        for (Session session: localMap.values()) {
            if (session instanceof SessionImpl) {
                SessionImpl ses = (SessionImpl) session;
                UserHolder h = (UserHolder) ses.data().get("__vertx.userHolder");
                if (h != null) {
                    if (h.user == null) {
                        h.user = h.context.user();
                    }
                }
                ses.writeToBuffer(buf);
            }
        }
        try {
            try (OutputStream os = new FileOutputStream(sessionStorage)) {
                os.write(buf.getBytes());
            }
        } catch (IOException e) {
            log.error("failed to save local sessions", e);
        }
    }



}
