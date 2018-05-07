package com.haulmont.components.samples.imap.core;

import com.haulmont.addon.imap.api.ImapAPI;
import com.haulmont.addon.imap.dto.ImapMessageDto;
import com.haulmont.addon.imap.entity.ImapMessage;
import com.haulmont.addon.imap.entity.ImapMailBox;
import com.haulmont.addon.imap.events.EmailAnsweredImapEvent;
import com.haulmont.addon.imap.events.NewEmailImapEvent;
import com.haulmont.components.samples.imap.entity.ImapDemoMessage;
import com.haulmont.cuba.core.EntityManager;
import com.haulmont.cuba.core.Persistence;
import com.haulmont.cuba.core.Transaction;
import com.haulmont.cuba.core.entity.BaseUuidEntity;
import com.haulmont.cuba.core.global.Metadata;
import com.haulmont.cuba.security.app.Authentication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class NewMessageListener {

    private final static Logger log = LoggerFactory.getLogger(NewMessageListener.class);

    private final Authentication authentication;
    private final Metadata metadata;
    private final Persistence persistence;
    private final ImapAPI imapAPI;
    private Timer timer;

    private final List<ImapMessage> messages = new ArrayList<>();

    @SuppressWarnings("CdiInjectionPointsInspection")
    @Inject
    public NewMessageListener(Authentication authentication, Metadata metadata, Persistence persistence, ImapAPI imapAPI) {
        this.authentication = authentication;
        this.metadata = metadata;
        this.persistence = persistence;
        this.imapAPI = imapAPI;
    }

    @EventListener
    public void handleNewEvent(NewEmailImapEvent event) {
        try (Transaction ignore = persistence.createTransaction()) {
            EntityManager em = persistence.getEntityManager();
            int sameUIDs = em.createQuery(
                    "select m from imapsample$ImapDemoMessage m where m.messageUid = :uid and m.mailBox.id = :mailBoxId"
            )
                    .setParameter("uid", event.getMessageId())
                    .setParameter("mailBoxId", event.getMessage().getFolder().getMailBox())
                    .getResultList()
                    .size();
            if (sameUIDs == 0) {
                synchronized (messages) {
                    if (timer != null) {
                        timer.cancel();
                    }
                    messages.add(event.getMessage());
                    timer = new Timer();
                    if (messages.size() > 20) {
                        flushTask().run();
                    } else {
                        timer.schedule(flushTask(), 5_000);
                    }
                }
            }
        }
    }

    @SuppressWarnings("unused")
    public void handlerAnsweredEvent(EmailAnsweredImapEvent event) {
        log.info("new answer: {}", event);
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
        List<ImapMessage> imapMessages;
        synchronized (messages) {
            imapMessages = new ArrayList<>(messages);
            messages.clear();
        }

        List<ImapMessageDto> dtos;
        try {
            dtos = imapAPI.fetchMessages(imapMessages);
        } catch (Exception e) {
            throw new RuntimeException("Can't handle new message event", e);
        }
        Map<MsgKey, UUID> msgIdsByKeys = imapMessages.stream().collect(Collectors.toMap(MsgKey::new, BaseUuidEntity::getId));
        authentication.begin();
        try (Transaction tx = persistence.createTransaction()) {
            EntityManager em = persistence.getEntityManager();
            Map<UUID, ImapMailBox> mailBoxes = new HashMap<>();
            for (ImapMessageDto dto : dtos) {
                ImapDemoMessage imapMsg = metadata.create(ImapDemoMessage.class);
                ImapDemoMessage.fillMessage(imapMsg, dto, () -> {
                    UUID mailBoxId = dto.getMailBox().getId();
                    ImapMailBox mailBox = mailBoxes.get(mailBoxId);
                    if (mailBox == null) {
                        mailBox = em.createQuery(
                                "select mb from imap$MailBox mb where mb.id = :mailBoxId",
                                ImapMailBox.class
                        ).setParameter("mailBoxId", mailBoxId).getFirstResult();
                        if (mailBox == null) {
                            return null;
                        }
                        mailBoxes.put(mailBoxId, mailBox);
                    }
                    return mailBox;
                });
                if (imapMsg.getMailBox() == null) {
                    continue;
                }
                imapMsg.setImapMessageId(msgIdsByKeys.get(new MsgKey(imapMsg)));
                em.persist(imapMsg);
            }
            tx.commit();
        } finally {
            authentication.end();
        }
    }

    private static class MsgKey {
        private final String mailboxId;
        private final String folderName;
        private final long uid;

        MsgKey(ImapDemoMessage msg) {
            this(msg.getMailBox().getId().toString(), msg.getFolderName(), msg.getMessageUid());
        }

        MsgKey(ImapMessage msg) {
            this(msg.getFolder().getMailBox().getId().toString(), msg.getFolder().getName(), msg.getMsgUid());
        }

        MsgKey(String mailboxId, String folderName, long uid) {
            this.mailboxId = mailboxId;
            this.folderName = folderName;
            this.uid = uid;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MsgKey msgKey = (MsgKey) o;
            return uid == msgKey.uid &&
                    Objects.equals(mailboxId, msgKey.mailboxId) &&
                    Objects.equals(folderName, msgKey.folderName);
        }

        @Override
        public int hashCode() {

            return Objects.hash(mailboxId, folderName, uid);
        }
    }
}
