package com.haulmont.components.samples.imap.core;

import com.haulmont.addon.imap.api.ImapAPI;
import com.haulmont.addon.imap.dto.ImapMessageDto;
import com.haulmont.addon.imap.entity.ImapMessage;
import com.haulmont.addon.imap.entity.ImapMailBox;
import com.haulmont.addon.imap.events.NewEmailImapEvent;
import com.haulmont.components.samples.imap.entity.ImapDemoMessage;
import com.haulmont.cuba.core.EntityManager;
import com.haulmont.cuba.core.Persistence;
import com.haulmont.cuba.core.Transaction;
import com.haulmont.cuba.core.entity.BaseUuidEntity;
import com.haulmont.cuba.core.global.Metadata;
import com.haulmont.cuba.security.app.Authentication;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class NewMessageListener {

    @Inject
    private Authentication authentication;

    @Inject
    private Metadata metadata;

    @Inject
    private Persistence persistence;

    @Inject
    private ImapAPI imapAPI;

    private Timer timer;

    private final List<ImapMessage> messages = new ArrayList<>();

    @EventListener
    public void handleNewEvent(NewEmailImapEvent event) {
        try (Transaction tx = persistence.createTransaction()) {
            EntityManager em = persistence.getEntityManager();
            int sameUids = em.createQuery(
                    "select m from imapsample$ImapDemoMessage m where m.messageUid = :uid and m.mailBox.id = :mailBoxId"
            )
                    .setParameter("uid", event.getMessageId())
                    .setParameter("mailBoxId", event.getMessage().getFolder().getMailBox())
                    .getResultList()
                    .size();
            if (sameUids == 0) {
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
        Collection<ImapMessage> msgs;
        synchronized (messages) {
            msgs = new ArrayList<>(messages);
            messages.clear();
        }

        Collection<ImapMessageDto> dtos;
        try {
            dtos = imapAPI.fetchMessages(msgs);
        } catch (Exception e) {
            throw new RuntimeException("Can't handle new message event", e);
        }
        Map<MsgKey, UUID> msgIdsByKeys = msgs.stream().collect(Collectors.toMap(MsgKey::new, BaseUuidEntity::getId));
        authentication.begin();
        try (Transaction tx = persistence.createTransaction()) {
            EntityManager em = persistence.getEntityManager();
            Map<UUID, ImapMailBox> mailBoxes = new HashMap<>();
            for (ImapMessageDto dto : dtos) {
                ImapDemoMessage imapMsg = metadata.create(ImapDemoMessage.class);
                ImapDemoMessage.fillMessage(imapMsg, dto, () -> {
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
        private String mailboxId;
        private String folderName;
        private long uid;

        public MsgKey(ImapDemoMessage msg) {
            this(msg.getMailBox().getId().toString(), msg.getFolderName(), msg.getMessageUid());
        }

        public MsgKey(ImapMessage msg) {
            this(msg.getFolder().getMailBox().getId().toString(), msg.getFolder().getName(), msg.getMsgUid());
        }

        public MsgKey(String mailboxId, String folderName, long uid) {
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
