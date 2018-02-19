package com.haulmont.components.samples.imap.web.demo;

import com.haulmont.bali.datastruct.Pair;
import com.haulmont.components.imap.dto.MailMessageDto;
import com.haulmont.components.imap.dto.MessageRef;
import com.haulmont.components.imap.service.ImapService;
import com.haulmont.components.samples.imap.entity.MailMessage;
import com.haulmont.cuba.core.global.CommitContext;
import com.haulmont.cuba.core.global.DataManager;
import com.haulmont.cuba.core.global.LoadContext;
import com.haulmont.cuba.core.global.TimeSource;
import com.haulmont.cuba.gui.components.AbstractWindow;
import com.haulmont.cuba.gui.components.Component;
import com.haulmont.cuba.gui.components.Table;
import com.haulmont.cuba.gui.components.Timer;
import com.haulmont.cuba.gui.data.CollectionDatasource;
import com.haulmont.cuba.gui.executors.*;
import com.haulmont.cuba.gui.xml.layout.ComponentsFactory;

import javax.inject.Inject;
import javax.mail.MessagingException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class Demo extends AbstractWindow {

    @Inject
    private ImapService service;

    @Inject
    private BackgroundWorker backgroundWorker;

    @Inject
    private DataManager dm;

    @Inject
    private ComponentsFactory componentsFactory;

    @Inject
    private CollectionDatasource<MailMessage, UUID> mailMessageDs;

    @Inject
    private TimeSource timeSource;

    @Inject
    private Table<MailMessage> mailMessageTable;

    @Override
    public void init(Map<String, Object> params) {
        showNewMessage();

        Timer timer = componentsFactory.createTimer();

        addTimer(timer);

        timer.setDelay(5_000);
        timer.setRepeating(true);

        timer.addActionListener(_timer -> showNewMessage());

        timer.start();
    }

    public void showNewMessage() {
        BackgroundTaskHandler taskHandler = backgroundWorker.handle(task());
        taskHandler.execute();
    }

    public void deleteMessage(Component component) {
        forEachSelected(pair -> {
            try {
                service.deleteMessage(pair.getSecond());
                dm.remove(pair.getFirst());
            } catch (MessagingException e) {
                throw new RuntimeException("delete error", e);
            }
        });
    }

    public void markAsRead() {
        forEachSelected(pair -> {
            try {
                MessageRef ref = pair.getSecond();
                service.markAsRead(ref);
                MailMessageDto dto = service.fetchMessage(ref);
                MailMessage mailMessage = pair.getFirst();
                MailMessage.fillMessage(mailMessage, dto, mailMessage::getMailBox);
                dm.commit(mailMessage);
            } catch (MessagingException e) {
                throw new RuntimeException("markAsRead error", e);
            }
        });
    }

    public void markAsImportant() {
        forEachSelected(pair -> {
            try {
                MessageRef ref = pair.getSecond();
                service.markAsImportant(ref);
                MailMessageDto dto = service.fetchMessage(ref);
                MailMessage mailMessage = pair.getFirst();
                MailMessage.fillMessage(mailMessage, dto, mailMessage::getMailBox);
                dm.commit(mailMessage);
            } catch (MessagingException e) {
                throw new RuntimeException("markAsImportant error", e);
            }
        });
    }

    private void forEachSelected(Consumer<Pair<MailMessage, MessageRef>> action) {
        mailMessageTable.getSelected().forEach(msg -> {
            MessageRef messageRef = new MessageRef();
            messageRef.setUid(msg.getMessageUid());
            messageRef.setMailBox(msg.getMailBox());
            messageRef.setFolderName(msg.getFolderName());
            action.accept(new Pair<>(msg, messageRef));
        });
        if (!mailMessageTable.getSelected().isEmpty()) {
            mailMessageDs.refresh();
        }
    }

    private BackgroundTask<Integer, Void> task() {
        UIAccessor uiAccessor = backgroundWorker.getUIAccessor();

        return new BackgroundTask<Integer, Void>(10, this) {
            private boolean added = false;

            @Override
            public Void run(TaskLifeCycle<Integer> taskLifeCycle) {
                MailMessage newMessage = dm.load(LoadContext.create(MailMessage.class).setQuery(
                        LoadContext.createQuery("select m from imapsample$MailMessage m where m.seen is null or m.seen = false").setMaxResults(1))
                        .setView("mailMessage-full"));
                added = newMessage != null;
                if (newMessage != null) {
                    newMessage.setSeen(true);
                    newMessage.setSeenTime(timeSource.currentTimestamp());
                    dm.commit(new CommitContext(newMessage));
                    uiAccessor.access(() ->
                        showNotification(
                                "New message arrived",
                                String.format("%s from %s", newMessage.getSubject(), newMessage.getFrom()),
                                NotificationType.TRAY
                        )
                    );
                }

                return null;
            }

            @Override
            public void canceled() {
                // Do something in UI thread if the task is canceled
            }

            @Override
            public void done(Void result) {
                //if (added) {
                    mailMessageDs.refresh();
                //}
            }

            @Override
            public void progress(List<Integer> changes) {
                // Show current progress in UI thread
            }
        };
    }

}