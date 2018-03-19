package com.haulmont.components.samples.imap.core;

import com.haulmont.components.imap.api.ImapAPI;
import com.haulmont.components.imap.dto.ImapMessageDto;
import com.haulmont.components.imap.entity.ImapMessageRef;
import com.haulmont.components.imap.entity.ImapMailBox;
import com.haulmont.components.imap.events.NewEmailImapEvent;
import com.haulmont.components.samples.imap.entity.ImapMessage;
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

    private final List<ImapMessageRef> messageRefs = new ArrayList<>();

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
                synchronized (messageRefs) {
                    if (timer != null) {
                        timer.cancel();
                    }
                    messageRefs.add(event.getMessageRef());
                    timer = new Timer();
                    if (messageRefs.size() > 20) {
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
        Collection<ImapMessageRef> refs;
        synchronized (messageRefs) {
            refs = new ArrayList<>(messageRefs);
            messageRefs.clear();
        }

        Collection<ImapMessageDto> dtos;
        try {
            dtos = imapAPI.fetchMessages(refs);
        } catch (Exception e) {
            throw new RuntimeException("Can't handle new message event", e);
        }
        Map<MsgKey, UUID> refsIdByKeys = refs.stream().collect(Collectors.toMap(MsgKey::new, BaseUuidEntity::getId));
        authentication.begin();
        try (Transaction tx = persistence.createTransaction()) {
            EntityManager em = persistence.getEntityManager();
            Map<UUID, ImapMailBox> mailBoxes = new HashMap<>();
            for (ImapMessageDto dto : dtos) {
                ImapMessage imapMsg = metadata.create(ImapMessage.class);
                ImapMessage.fillMessage(imapMsg, dto, () -> {
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
                imapMsg.setImapMessageId(refsIdByKeys.get(new MsgKey(imapMsg)));
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

        public MsgKey(ImapMessage msg) {
            this(msg.getMailBox().getId().toString(), msg.getFolderName(), msg.getMessageUid());
        }

        public MsgKey(ImapMessageRef ref) {
            this(ref.getFolder().getMailBox().getId().toString(), ref.getFolder().getName(), ref.getMsgUid());
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
