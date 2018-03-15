package com.haulmont.components.samples.imap.core;

import com.haulmont.components.imap.dto.ImapMessageDto;
import com.haulmont.components.imap.entity.ImapMessageRef;
import com.haulmont.components.imap.entity.ImapMailBox;
import com.haulmont.components.imap.events.NewEmailImapEvent;
import com.haulmont.components.imap.service.ImapAPIService;
import com.haulmont.components.samples.imap.entity.ImapMessage;
import com.haulmont.cuba.core.EntityManager;
import com.haulmont.cuba.core.Persistence;
import com.haulmont.cuba.core.Transaction;
import com.haulmont.cuba.core.global.Metadata;
import com.haulmont.cuba.security.app.Authentication;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.util.*;

@Component
public class NewMessageListener {
    @Inject
    private Authentication authentication;

    @Inject
    private Metadata metadata;

    @Inject
    private Persistence persistence;

    @Inject
    private ImapAPIService imapAPI;

    private Timer timer;

    private volatile List<ImapMessageRef> messageRefs = new ArrayList<>();

    @EventListener
    public void handleNewEvent(NewEmailImapEvent event) {
        try (Transaction tx = persistence.createTransaction()) {
            EntityManager em = persistence.getEntityManager();
            int sameUids = em.createQuery(
                    "select m from imapsample$ImapMessage m where m.messageUid = :uid and m.mailBox.id = :mailBoxId"
            )
                    .setParameter("uid", event.getMessageId())
                    .setParameter("mailBoxId", event.getMessageRef().getFolder().getMailBox())
                    .getResultList()
                    .size();
            if (sameUids == 0) {
                if (timer != null) {
                    timer.cancel();
                }
                synchronized (messageRefs) {
                    messageRefs.add(event.getMessageRef());
                    timer = new Timer();
                    if (messageRefs.size() > 20) {
                        timer.schedule(flushTask(), 0);
                    } else {
                        timer.schedule(flushTask(), 5_000);
                    }
                }
            }
        }
    }

    private TimerTask flushTask() {
        return new TimerTask() {
            @Override
            public void run() {
                authentication.begin();
                try {
                    flush();
                } finally {
                    authentication.end();
                }
            }
        };
    }

    private void flush() {
        Collection<ImapMessageDto> dtos;
        synchronized (messageRefs) {
            try {
                dtos = imapAPI.fetchMessages(messageRefs);
            } catch (Exception e) {
                throw new RuntimeException("Can't handle new message event", e);
            } finally {
                messageRefs.clear();
            }
        }
        authentication.begin();
        try (Transaction tx = persistence.createTransaction()) {
            EntityManager em = persistence.getEntityManager();
            Map<UUID, ImapMailBox> mailBoxes = new HashMap<>();
            for (ImapMessageDto dto : dtos) {
                ImapMessage mailMessage = metadata.create(ImapMessage.class);
                ImapMessage.fillMessage(mailMessage, dto, () -> {
                    ImapMailBox mailBox = mailBoxes.get(dto.getMailBoxId());
                    if (mailBox == null) {
                        mailBox = em.createQuery(
                                "select mb from imapcomponent$ImapMailBox mb where mb.id = :mailBoxId",
                                ImapMailBox.class
                        ).setParameter("mailBoxId", dto.getMailBoxId()).getFirstResult();
                        if (mailBox == null) {
                            return null;
                        }
                        mailBoxes.put(dto.getMailBoxId(), mailBox);
                    }
                    return mailBox;
                });
                if (mailMessage.getMailBox() == null) {
                    continue;
                }
                em.persist(mailMessage);
            }
            tx.commit();
        } finally {
            authentication.end();
        }
    }
}
