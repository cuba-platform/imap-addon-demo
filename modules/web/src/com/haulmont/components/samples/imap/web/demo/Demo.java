package com.haulmont.components.samples.imap.web.demo;

import com.haulmont.bali.datastruct.Pair;
import com.haulmont.addon.imap.dto.ImapMessageDto;
import com.haulmont.addon.imap.entity.ImapMessage;
import com.haulmont.addon.imap.api.ImapFlag;
import com.haulmont.components.samples.imap.entity.ImapDemoMessage;
import com.haulmont.components.samples.imap.service.ImapDemoService;
import com.haulmont.cuba.core.global.CommitContext;
import com.haulmont.cuba.core.global.DataManager;
import com.haulmont.cuba.core.global.LoadContext;
import com.haulmont.cuba.core.global.TimeSource;
import com.haulmont.cuba.gui.WindowManager;
import com.haulmont.cuba.gui.components.AbstractWindow;
import com.haulmont.cuba.gui.components.Table;
import com.haulmont.cuba.gui.components.TextField;
import com.haulmont.cuba.gui.components.Timer;
import com.haulmont.cuba.gui.data.CollectionDatasource;
import com.haulmont.cuba.gui.executors.*;
import com.haulmont.cuba.gui.xml.layout.ComponentsFactory;

import javax.inject.Inject;
import java.util.*;
import java.util.function.Consumer;

@SuppressWarnings({"CdiInjectionPointsInspection", "SpringJavaAutowiredFieldsWarningInspection"})
public class Demo extends AbstractWindow {

    @Inject
    private ImapDemoService imapAPI;

    @Inject
    private BackgroundWorker backgroundWorker;

    @Inject
    private DataManager dm;

    @Inject
    private ComponentsFactory componentsFactory;

    @Inject
    private CollectionDatasource<ImapDemoMessage, UUID> imapDemoMessageDs;

    @Inject
    private TimeSource timeSource;

    @Inject
    private Table<ImapDemoMessage> imapDemoMessageTable;

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

    @SuppressWarnings("WeakerAccess")
    public void showNewMessage() {
        BackgroundTaskHandler taskHandler = backgroundWorker.handle(task());
        taskHandler.execute();
    }

    public void viewMessage() {
        forEachSelected(pair -> {
            ImapMessage msg = pair.getSecond();
            openEditor(msg, WindowManager.OpenType.THIS_TAB);
        });
    }

    public void deleteMessage() {
        forEachSelected(pair -> {
            imapAPI.deleteMessage(pair.getSecond());
            dm.remove(pair.getFirst());
        });
    }

    public void markAsRead() {
        forEachSelected(pair -> {
            ImapMessage msg = pair.getSecond();
            imapAPI.setFlag(msg, ImapFlag.SEEN, true);
            updateMessage(pair, msg);
        });
    }

    public void markAsImportant() {
        forEachSelected(pair -> {
            ImapMessage msg = pair.getSecond();
            imapAPI.setFlag(msg, ImapFlag.IMPORTANT, true);
            updateMessage(pair, msg);
        });
    }

    public void setFlag() {
        String flagName = flagNameField.getRawValue();
        if (flagName == null) {
            return;
        }
        forEachSelected(pair -> {
            ImapMessage msg = pair.getSecond();
            imapAPI.setFlag(msg, new ImapFlag(flagName), true);
            updateMessage(pair, msg);
        });
    }

    public void unsetFlag() {
        String flagName = flagNameField.getRawValue();
        if (flagName == null) {
            return;
        }
        forEachSelected(pair -> {
            ImapMessage msg = pair.getSecond();
            imapAPI.setFlag(msg, new ImapFlag(flagName), false);
            updateMessage(pair, msg);
        });
    }

    private void updateMessage(Pair<ImapDemoMessage, ImapMessage> pair, ImapMessage msg) {
        ImapMessageDto dto = imapAPI.fetchMessage(msg);
        ImapDemoMessage imapMessage = pair.getFirst();
        ImapDemoMessage.fillMessage(imapMessage, dto, imapMessage::getMailBox);
        dm.commit(imapMessage);
    }

    public void moveToFolder() {
        String folderName = folderNameField.getRawValue();
        if (folderName == null) {
            return;
        }
        forEachSelected(pair -> {
            ImapMessage msg = pair.getSecond();
            imapAPI.moveMessage(msg, folderName);
        });

    }

    @SuppressWarnings("IncorrectCreateEntity")
    private void forEachSelected(Consumer<Pair<ImapDemoMessage, ImapMessage>> action) {
        imapDemoMessageTable.getSelected().forEach(msg -> {
            UUID imapMessageId = msg.getImapMessageId();
            ImapMessage message = dm.load(LoadContext.create(ImapMessage.class)
                    .setId(imapMessageId).setView("imap-msg-full")
            );
            action.accept(new Pair<>(msg, message));
        });
        if (!imapDemoMessageTable.getSelected().isEmpty()) {
            imapDemoMessageDs.refresh();
        }
    }

    private BackgroundTask<Integer, Void> task() {
        UIAccessor uiAccessor = backgroundWorker.getUIAccessor();

        return new BackgroundTask<Integer, Void>(10, this) {
            @Override
            public Void run(TaskLifeCycle<Integer> taskLifeCycle) {
                ImapDemoMessage newMessage = dm.load(LoadContext.create(ImapDemoMessage.class).setQuery(
                        LoadContext.createQuery("select m from imapsample$ImapDemoMessage m where m.seen is null or m.seen = false").setMaxResults(1))
                        .setView("imapDemoMessage-full"));
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
                imapDemoMessageDs.refresh();
            }

            @Override
            public void progress(List<Integer> changes) {
                // Show current progress in UI thread
            }
        };
    }

}