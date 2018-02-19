package com.haulmont.components.samples.imap.core;

import com.haulmont.components.imap.dto.MessageRef;
import com.haulmont.components.imap.events.EmailDeletedEvent;
import com.haulmont.components.samples.imap.entity.MailMessage;
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

    @Inject
    private Persistence persistence;

    @Inject
    private Authentication authentication;

    @EventListener
    public void handEmailDeleted(EmailDeletedEvent event) {
        log.info("deleted:{}", event);
        MessageRef messageRef = event.getMessageRef();
        authentication.begin();
        try (Transaction tx = persistence.createTransaction()) {
            EntityManager em = persistence.getEntityManager();
            em.createQuery(
                    "delete from imapsample$MailMessage m where m.messageUid = :uid and m.mailBox.id = :mailBoxId",
                    MailMessage.class
            )
                    .setParameter("uid", messageRef.getUid())
                    .setParameter("mailBoxId", messageRef.getMailBox())
                    .executeUpdate();

            tx.commit();
        } finally {
            authentication.end();
        }
    }

}
