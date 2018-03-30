package com.haulmont.components.samples.imap.service;

import com.haulmont.addon.imap.api.ImapAPI;
import com.haulmont.addon.imap.entity.ImapMessage;
import com.haulmont.addon.imap.events.EmailFlagChangedImapEvent;
import com.haulmont.components.samples.imap.entity.ImapDemoMessage;
import com.haulmont.cuba.core.EntityManager;
import com.haulmont.cuba.core.Persistence;
import com.haulmont.cuba.core.Transaction;
import com.haulmont.cuba.security.app.Authentication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.inject.Inject;

@Service(ModifiedMessageService.NAME)
@SuppressWarnings({"CdiInjectionPointsInspection", "SpringJavaAutowiredFieldsWarningInspection"})
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
        ImapMessage message = event.getMessage();
        authentication.begin();
        try (Transaction tx = persistence.createTransaction()) {
            EntityManager em = persistence.getEntityManager();
            ImapDemoMessage msg = em.createQuery(
                    "select m from imapsample$ImapDemoMessage m where m.messageUid = :uid and m.mailBox.id = :mailBoxId",
                    ImapDemoMessage.class
            )
                    .setParameter("uid", message.getMsgUid())
                    .setParameter("mailBoxId", message.getFolder().getMailBox())
                    .getFirstResult();

            if (msg == null) {
                return;
            }

            ImapDemoMessage.fillMessage(msg, imapAPI.fetchMessage(message), () -> message.getFolder().getMailBox());

            em.persist(msg);
            tx.commit();
        } finally {
            authentication.end();
        }
    }
}