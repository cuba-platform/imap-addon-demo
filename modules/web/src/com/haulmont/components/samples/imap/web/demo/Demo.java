package com.haulmont.components.samples.imap.web.demo;

import com.haulmont.bali.datastruct.Pair;
import com.haulmont.components.imap.dto.ImapMessageDto;
import com.haulmont.components.imap.entity.ImapMessageRef;
import com.haulmont.components.imap.entity.ImapFolder;
import com.haulmont.components.imap.service.ImapAPIService;
import com.haulmont.components.imap.service.ImapFlag;
import com.haulmont.components.samples.imap.entity.ImapMessage;
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
    private CollectionDatasource<ImapMessage, UUID> imapMessageDs;

    @Inject
    private TimeSource timeSource;

    @Inject
    private Table<ImapMessage> imapMessageTable;

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
                dm.remove(pair.getFirst());
            } catch (MessagingException e) {
                throw new RuntimeException("delete error", e);
            }
        });
    }

    public void markAsRead() {
        forEachSelected(pair -> {
            try {
                ImapMessageRef ref = pair.getSecond();
                imapAPI.setFlag(ref, ImapFlag.SEEN, true);
                updateMessage(pair, ref);
            } catch (MessagingException e) {
                throw new RuntimeException("markAsRead error", e);
            }
        });
    }

    public void markAsImportant() {
        forEachSelected(pair -> {
            try {
                ImapMessageRef ref = pair.getSecond();
                imapAPI.setFlag(ref, ImapFlag.IMPORTANT, true);
                updateMessage(pair, ref);
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
                imapAPI.setFlag(ref, new ImapFlag(flagName), true);
                updateMessage(pair, ref);
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
                imapAPI.setFlag(ref, new ImapFlag(flagName), false);
                updateMessage(pair, ref);
            } catch (MessagingException e) {
                throw new RuntimeException("unset flag " + flagName + " error", e);
            }
        });
    }

    private void updateMessage(Pair<ImapMessage, ImapMessageRef> pair, ImapMessageRef ref) throws MessagingException {
        ImapMessageDto dto = imapAPI.fetchMessage(ref);
        ImapMessage imapMessage = pair.getFirst();
        ImapMessage.fillMessage(imapMessage, dto, imapMessage::getMailBox);
        dm.commit(imapMessage);
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
                ImapMessageDto dto = imapAPI.fetchMessage(ref);
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
    private void forEachSelected(Consumer<Pair<ImapMessage, ImapMessageRef>> action) {
        imapMessageTable.getSelected().forEach(msg -> {
            ImapMessageRef messageRef = new ImapMessageRef();
            messageRef.setMsgUid(msg.getMessageUid());
            ImapFolder folder = new ImapFolder();
            folder.setMailBox(msg.getMailBox());
            folder.setName(msg.getFolderName());
            messageRef.setFolder(folder);
            action.accept(new Pair<>(msg, messageRef));
        });
        if (!imapMessageTable.getSelected().isEmpty()) {
            imapMessageDs.refresh();
        }
    }

    private BackgroundTask<Integer, Void> task() {
        UIAccessor uiAccessor = backgroundWorker.getUIAccessor();

        return new BackgroundTask<Integer, Void>(10, this) {
            @Override
            public Void run(TaskLifeCycle<Integer> taskLifeCycle) {
                ImapMessage newMessage = dm.load(LoadContext.create(ImapMessage.class).setQuery(
                        LoadContext.createQuery("select m from imapsample$ImapMessage m where m.seen is null or m.seen = false").setMaxResults(1))
                        .setView("imapMessage-full"));
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
                imapMessageDs.refresh();
            }

            @Override
            public void progress(List<Integer> changes) {
                // Show current progress in UI thread
            }
        };
    }

}