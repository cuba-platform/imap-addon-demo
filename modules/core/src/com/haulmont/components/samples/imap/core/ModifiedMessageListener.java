package com.haulmont.components.samples.imap.core;

import com.haulmont.components.imap.dto.MessageRef;
import com.haulmont.components.imap.events.*;
import com.haulmont.components.imap.service.ImapService;
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
import javax.mail.MessagingException;

@Component
public class ModifiedMessageListener {

    private final static Logger log = LoggerFactory.getLogger(ModifiedMessageListener.class);

    @Inject
    private Persistence persistence;

    @Inject
    private Authentication authentication;

    @Inject
    private ImapService service;

    /*@EventListener
    public void handEmailMoved(EmailMovedEvent event) {
        log.info("moved:{}", event);
    }

    @EventListener
    public void handleEmailAnswered(EmailAnsweredEvent event) {
        log.info("answered:{}", event);
    }

    @EventListener
    public void handleEmailSeen(EmailSeenEvent event) {
        log.info("seen:{}", event);
    }*/

    @EventListener
    public void handleEvent(EmailFlagChangedEvent event) {
        log.info("handle event :{}", event);
        MessageRef messageRef = event.getMessageRef();
        authentication.begin();
        try (Transaction tx = persistence.createTransaction()) {
            EntityManager em = persistence.getEntityManager();
            MailMessage msg = em.createQuery(
                    "select m from imapsample$MailMessage m where m.messageUid = :uid and m.mailBox.id = :mailBoxId",
                    MailMessage.class
            )
                    .setParameter("uid", messageRef.getUid())
                    .setParameter("mailBoxId", messageRef.getMailBox())
                    .getFirstResult();

            if (msg == null) {
                return;
            }

            MailMessage.fillMessage(msg, service.fetchMessage(messageRef), messageRef::getMailBox);

            em.persist(msg);
            tx.commit();
        } catch (MessagingException e) {
            throw new RuntimeException("Can't handle event " + event + ". Messaging error", e);
        } finally {
            authentication.end();
        }
    }
}
