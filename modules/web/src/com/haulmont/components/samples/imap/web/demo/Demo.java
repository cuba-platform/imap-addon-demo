package com.haulmont.components.samples.imap.web.demo;

import com.haulmont.bali.datastruct.Pair;
import com.haulmont.components.imap.dto.MailMessageDto;
import com.haulmont.components.imap.entity.ImapMessageRef;
import com.haulmont.components.imap.entity.MailFolder;
import com.haulmont.components.imap.service.ImapAPIService;
import com.haulmont.components.samples.imap.entity.MailMessage;
import com.haulmont.cuba.core.entity.FileDescriptor;
import com.haulmont.cuba.core.global.CommitContext;
import com.haulmont.cuba.core.global.DataManager;
import com.haulmont.cuba.core.global.LoadContext;
import com.haulmont.cuba.core.global.TimeSource;
import com.haulmont.cuba.gui.components.AbstractWindow;
import com.haulmont.cuba.gui.components.Table;
import com.haulmont.cuba.gui.components.TextField;
import com.haulmont.cuba.gui.components.Timer;
import com.haulmont.cuba.gui.data.CollectionDatasource;
import com.haulmont.cuba.gui.executors.*;
import com.haulmont.cuba.gui.xml.layout.ComponentsFactory;

import javax.inject.Inject;
import javax.mail.MessagingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class Demo extends AbstractWindow {

    @Inject
    private ImapAPIService imapAPI;

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

    @Inject
    private TextField flagNameField;

    @Inject
    private TextField folderNameField;

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

    public void deleteMessage() {
        forEachSelected(pair -> {
            try {
                imapAPI.deleteMessage(pair.getSecond());
            } catch (MessagingException e) {
                throw new RuntimeException("delete error", e);
            }
        });
    }

    public void markAsRead() {
        forEachSelected(pair -> {
            try {
                ImapMessageRef ref = pair.getSecond();
                imapAPI.setFlag(ref, ImapAPIService.Flag.SEEN, true);
            } catch (MessagingException e) {
                throw new RuntimeException("markAsRead error", e);
            }
        });
    }

    public void markAsImportant() {
        forEachSelected(pair -> {
            try {
                ImapMessageRef ref = pair.getSecond();
                imapAPI.setFlag(ref, ImapAPIService.Flag.IMPORTANT, true);
            } catch (MessagingException e) {
                throw new RuntimeException("markAsImportant error", e);
            }
        });
    }

    public void setFlag() {
        String flagName = flagNameField.getRawValue();
        if (flagName == null) {
            return;
        }
        forEachSelected(pair -> {
            try {
                ImapMessageRef ref = pair.getSecond();
                imapAPI.setFlag(ref, new ImapAPIService.Flag(flagName), true);
            } catch (MessagingException e) {
                throw new RuntimeException("set flag " + flagName + " error", e);
            }
        });
    }

    public void unsetFlag() {
        String flagName = flagNameField.getRawValue();
        if (flagName == null) {
            return;
        }
        forEachSelected(pair -> {
            try {
                ImapMessageRef ref = pair.getSecond();
                imapAPI.setFlag(ref, new ImapAPIService.Flag(flagName), false);
            } catch (MessagingException e) {
                throw new RuntimeException("unset flag " + flagName + " error", e);
            }
        });
    }

    public void moveToFolder() {
        String folderName = folderNameField.getRawValue();
        if (folderName == null) {
            return;
        }
        forEachSelected(pair -> {
            try {
                ImapMessageRef ref = pair.getSecond();
                imapAPI.moveMessage(ref, folderName);
            } catch (MessagingException e) {
                throw new RuntimeException("move to folder " + folderName + " error", e);
            }
        });

    }

    public void showBody() {
        List<String> body = new ArrayList<>();

        final boolean[] isHtml = {false};

        forEachSelected(pair -> {
            try {
                ImapMessageRef ref = pair.getSecond();
                MailMessageDto dto = imapAPI.fetchMessage(ref);
                isHtml[0] |= Boolean.TRUE.equals(dto.getHtml());
                body.add(dto.getBody());
            } catch (MessagingException e) {
                throw new RuntimeException("fetch body error", e);
            }
        });

        showNotification(
                "Body of selected",
                body.toString(),
                isHtml[0] ? NotificationType.WARNING_HTML : NotificationType.WARNING
        );
    }

    public void showAttachments() {
        List<FileDescriptor> attachments = new ArrayList<>();

        forEachSelected(pair -> {
            try {
                ImapMessageRef ref = pair.getSecond();
                attachments.addAll(imapAPI.fetchAttachments(ref));
            } catch (MessagingException e) {
                throw new RuntimeException("fetch attachments error", e);
            }
        });

        showNotification(
                "Attachments of selected",
                attachments.toString(),
                NotificationType.WARNING
        );
    }

    @SuppressWarnings("IncorrectCreateEntity")
    private void forEachSelected(Consumer<Pair<MailMessage, ImapMessageRef>> action) {
        mailMessageTable.getSelected().forEach(msg -> {
            ImapMessageRef messageRef = new ImapMessageRef();
            messageRef.setMsgUid(msg.getMessageUid());
            MailFolder folder = new MailFolder();
            folder.setMailBox(msg.getMailBox());
            folder.setName(msg.getFolderName());
            messageRef.setFolder(folder);
            action.accept(new Pair<>(msg, messageRef));
        });
        if (!mailMessageTable.getSelected().isEmpty()) {
            mailMessageDs.refresh();
        }
    }

    private BackgroundTask<Integer, Void> task() {
        UIAccessor uiAccessor = backgroundWorker.getUIAccessor();

        return new BackgroundTask<Integer, Void>(10, this) {
            @Override
            public Void run(TaskLifeCycle<Integer> taskLifeCycle) {
                MailMessage newMessage = dm.load(LoadContext.create(MailMessage.class).setQuery(
                        LoadContext.createQuery("select m from imapsample$MailMessage m where m.seen is null or m.seen = false").setMaxResults(1))
                        .setView("mailMessage-full"));
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
                mailMessageDs.refresh();
            }

            @Override
            public void progress(List<Integer> changes) {
                // Show current progress in UI thread
            }
        };
    }

}