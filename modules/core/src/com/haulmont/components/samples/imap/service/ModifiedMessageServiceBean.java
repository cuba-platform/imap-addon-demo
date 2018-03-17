package com.haulmont.components.samples.imap.service;

import com.haulmont.components.imap.api.ImapAPI;
import com.haulmont.components.imap.entity.ImapMessageRef;
import com.haulmont.components.imap.events.EmailFlagChangedImapEvent;
import com.haulmont.components.samples.imap.entity.ImapMessage;
import com.haulmont.cuba.core.EntityManager;
import com.haulmont.cuba.core.Persistence;
import com.haulmont.cuba.core.Transaction;
import com.haulmont.cuba.security.app.Authentication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.inject.Inject;
import javax.mail.MessagingException;

@Service(ModifiedMessageService.NAME)
public class ModifiedMessageServiceBean implements ModifiedMessageService {
    private final static Logger log = LoggerFactory.getLogger(ModifiedMessageServiceBean.class);

    @Inject
    private Persistence persistence;

    @Inject
    private Authentication authentication;

    @Inject
    private ImapAPI imapAPI;

    public void handleEvent(EmailFlagChangedImapEvent event) {
        log.info("handle event :{}", event);
        ImapMessageRef messageRef = event.getMessageRef();
        authentication.begin();
        try (Transaction tx = persistence.createTransaction()) {
            EntityManager em = persistence.getEntityManager();
            ImapMessage msg = em.createQuery(
                    "select m from imapsample$ImapMessage m where m.messageUid = :uid and m.mailBox.id = :mailBoxId",
                    ImapMessage.class
            )
                    .setParameter("uid", messageRef.getMsgUid())
                    .setParameter("mailBoxId", messageRef.getFolder().getMailBox())
                    .getFirstResult();

            if (msg == null) {
                return;
            }

            ImapMessage.fillMessage(msg, imapAPI.fetchMessage(messageRef), () -> messageRef.getFolder().getMailBox());

            em.persist(msg);
            tx.commit();
        } catch (MessagingException e) {
            throw new RuntimeException("Can't handle event " + event + ". Messaging error", e);
        } finally {
            authentication.end();
        }
    }
}