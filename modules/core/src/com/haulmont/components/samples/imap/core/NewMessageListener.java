package com.haulmont.components.samples.imap.core;

import com.haulmont.addon.imap.api.ImapAPI;
import com.haulmont.addon.imap.dto.ImapMessageDto;
import com.haulmont.addon.imap.entity.ImapMailBox;
import com.haulmont.addon.imap.entity.ImapMessage;
import com.haulmont.addon.imap.events.EmailAnsweredImapEvent;
import com.haulmont.addon.imap.events.NewEmailImapEvent;
import com.haulmont.components.samples.imap.entity.ImapDemoMessage;
import com.haulmont.cuba.core.EntityManager;
import com.haulmont.cuba.core.Persistence;
import com.haulmont.cuba.core.Transaction;
import com.haulmont.cuba.core.global.Metadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.util.UUID;

@Component
public class NewMessageListener {

    private final static Logger log = LoggerFactory.getLogger(NewMessageListener.class);

    private final Metadata metadata;
    private final Persistence persistence;
    private final ImapAPI imapAPI;


    @SuppressWarnings("CdiInjectionPointsInspection")
    @Inject
    public NewMessageListener(Metadata metadata, Persistence persistence, ImapAPI imapAPI) {
        this.metadata = metadata;
        this.persistence = persistence;
        this.imapAPI = imapAPI;
    }

    @EventListener
    public void handleNewEvent(NewEmailImapEvent event) {
        try (Transaction tx = persistence.createTransaction()) {
            EntityManager em = persistence.getEntityManager();
            int sameUIDs = em.createQuery(
                    "select m from imapsample$ImapDemoMessage m where m.messageUid = :uid and m.mailBox.id = :mailBoxId"
            )
                    .setParameter("uid", event.getMessageId())
                    .setParameter("mailBoxId", event.getMessage().getFolder().getMailBox().getId())
                    .getResultList()
                    .size();
            if (sameUIDs == 0) {
                ImapMessage msg = event.getMessage();
                ImapMessageDto dto = imapAPI.fetchMessage(msg);
                ImapDemoMessage imapMsg = metadata.create(ImapDemoMessage.class);
                ImapDemoMessage.fillMessage(imapMsg, dto, () -> {
                    UUID mailBoxId = dto.getMailBox().getId();
                    return em.createQuery(
                            "select mb from imap$MailBox mb where mb.id = :mailBoxId",
                            ImapMailBox.class
                    ).setParameter("mailBoxId", mailBoxId).getFirstResult();
                });
                if (imapMsg.getMailBox() == null) {
                    return;
                }
                imapMsg.setImapMessageId(msg.getId());
                em.persist(imapMsg);

                tx.commit();
            }
        }
    }

    @SuppressWarnings("unused")
    public void handlerAnsweredEvent(EmailAnsweredImapEvent event) {
        log.info("new answer: {}", event);
    }

}
