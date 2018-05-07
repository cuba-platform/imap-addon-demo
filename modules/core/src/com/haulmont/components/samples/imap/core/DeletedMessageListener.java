package com.haulmont.components.samples.imap.core;

import com.haulmont.addon.imap.entity.ImapMessage;
import com.haulmont.addon.imap.events.BaseImapEvent;
import com.haulmont.addon.imap.events.EmailDeletedImapEvent;
import com.haulmont.addon.imap.events.EmailMovedImapEvent;
import com.haulmont.components.samples.imap.entity.ImapDemoMessage;
import com.haulmont.cuba.core.EntityManager;
import com.haulmont.cuba.core.Persistence;
import com.haulmont.cuba.core.Transaction;
import com.haulmont.cuba.security.app.Authentication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.inject.Inject;

@Component
public class DeletedMessageListener {

    private final static Logger log = LoggerFactory.getLogger(DeletedMessageListener.class);

    private final Persistence persistence;
    private final Authentication authentication;

    @SuppressWarnings("CdiInjectionPointsInspection")
    @Inject
    public DeletedMessageListener(Persistence persistence, Authentication authentication) {
        this.persistence = persistence;
        this.authentication = authentication;
    }

    @EventListener
    public void handEmailDeleted(EmailDeletedImapEvent event) {
        log.info("deleted:{}", event);
        handle(event);
    }

    @EventListener
    public void handEmailDeleted(EmailMovedImapEvent event) {
        log.info("moved:{}", event);
        handle(event);
    }

    private void handle(BaseImapEvent event) {
        ImapMessage message = event.getMessage();
        authentication.begin();
        try (Transaction tx = persistence.createTransaction()) {
            EntityManager em = persistence.getEntityManager();
            em.createQuery(
                    "delete from imapsample$ImapDemoMessage m where m.messageUid = :uid and m.mailBox.id = :mailBoxId",
                    ImapDemoMessage.class
            )
                    .setParameter("uid", message.getMsgUid())
                    .setParameter("mailBoxId", message.getFolder().getMailBox())
                    .executeUpdate();

            tx.commit();
        } finally {
            authentication.end();
        }
    }

}
