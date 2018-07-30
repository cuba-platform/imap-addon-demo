package com.haulmont.components.samples.imap.web.demo;

import com.haulmont.addon.imap.api.ImapFlag;
import com.haulmont.addon.imap.dto.ImapMessageDto;
import com.haulmont.addon.imap.entity.ImapMessage;
import com.haulmont.bali.datastruct.Pair;
import com.haulmont.components.samples.imap.entity.ImapDemoMessage;
import com.haulmont.components.samples.imap.service.ImapDemoService;
import com.haulmont.cuba.core.global.CommitContext;
import com.haulmont.cuba.core.global.DataManager;
import com.haulmont.cuba.core.global.LoadContext;
import com.haulmont.cuba.core.global.TimeSource;
import com.haulmont.cuba.gui.WindowManager;
import com.haulmont.cuba.gui.components.AbstractWindow;
import com.haulmont.cuba.gui.components.SuggestionField;
import com.haulmont.cuba.gui.components.Table;
import com.haulmont.cuba.gui.components.Timer;
import com.haulmont.cuba.gui.data.CollectionDatasource;
import com.haulmont.cuba.gui.executors.*;
import com.haulmont.cuba.gui.xml.layout.ComponentsFactory;
import org.apache.commons.lang.StringUtils;

import javax.inject.Inject;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@SuppressWarnings({"CdiInjectionPointsInspection", "SpringJavaAutowiredFieldsWarningInspection"})
public class Demo extends AbstractWindow {

    @Inject
    private ImapDemoService imapAPI;

    @Inject
    private BackgroundWorker backgroundWorker;

    @Inject
    private DataManager dataManager;

    @Inject
    private ComponentsFactory componentsFactory;

    @Inject
    private CollectionDatasource<ImapDemoMessage, UUID> imapDemoMessageDs;

    @Inject
    private TimeSource timeSource;

    @Inject
    private Table<ImapDemoMessage> imapDemoMessageTable;

    @Inject
    private SuggestionField flagField;

    @Inject
    private SuggestionField folderField;

    @Override
    public void init(Map<String, Object> params) {
        showNewMessages();

        Timer timer = componentsFactory.createTimer();
        addTimer(timer);
        timer.setDelay(5_000);
        timer.setRepeating(true);
        timer.addActionListener(_timer -> showNewMessages());
        timer.start();

        updateFlagsSuggestionList();
        updateFoldersSuggestionList();
    }

    @SuppressWarnings("WeakerAccess")
    public void showNewMessages() {
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
            dataManager.remove(pair.getFirst());
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
        String flagName = flagField.getValue();
        if (flagName == null) {
            return;
        }
        forEachSelected(pair -> {
            ImapMessage msg = pair.getSecond();
            imapAPI.setFlag(msg, new ImapFlag(flagName), true);
            updateMessage(pair, msg);
        });

        updateFlagsSuggestionList();
    }

    public void unsetFlag() {
        String flagName = flagField.getValue();
        if (flagName == null) {
            return;
        }
        forEachSelected(pair -> {
            ImapMessage msg = pair.getSecond();
            imapAPI.setFlag(msg, new ImapFlag(flagName), false);
            updateMessage(pair, msg);
        });

        updateFlagsSuggestionList();
    }

    private void updateMessage(Pair<ImapDemoMessage, ImapMessage> pair, ImapMessage msg) {
        ImapMessageDto dto = imapAPI.fetchMessage(msg);
        ImapDemoMessage imapMessage = pair.getFirst();
        ImapDemoMessage.fillMessage(imapMessage, dto, imapMessage::getMailBox);
        dataManager.commit(imapMessage);
    }

    public void moveToFolder() {
        String folderName = folderField.getValue();
        if (StringUtils.isEmpty(folderName)) {
            return;
        }
        forEachSelected(pair -> {
            ImapMessage msg = pair.getSecond();
            imapAPI.moveMessage(msg, folderName);
        });

        updateFoldersSuggestionList();
    }

    @SuppressWarnings("IncorrectCreateEntity")
    private void forEachSelected(Consumer<Pair<ImapDemoMessage, ImapMessage>> action) {
        imapDemoMessageTable.getSelected().forEach(msg -> {
            UUID imapMessageId = msg.getImapMessageId();
            ImapMessage message = dataManager.load(LoadContext.create(ImapMessage.class)
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
                ImapDemoMessage newMessage = dataManager.load(LoadContext.create(ImapDemoMessage.class).setQuery(
                        LoadContext.createQuery("select m from imapsample$ImapDemoMessage m where m.seen is null or m.seen = false").setMaxResults(1))
                        .setView("imapDemoMessage-full"));
                if (newMessage != null) {
                    newMessage.setSeen(true);
                    newMessage.setSeenTime(timeSource.currentTimestamp());
                    dataManager.commit(new CommitContext(newMessage));
                    uiAccessor.access(() ->
                            showNotification(
                                    "New message arrived",
                                    String.format("%s from %s", newMessage.getSubject(), newMessage.getFrom()),
                                    NotificationType.TRAY
                            )
                    );
                } else {
                    uiAccessor.access(() ->
                        showNotification("Now new messages found", NotificationType.TRAY)
                    );
                }
                return null;
            }

            @Override
            public void canceled() {
                showNotification("The task was cancelled", NotificationType.TRAY);
            }

            @Override
            public void done(Void result) {
                imapDemoMessageDs.refresh();
            }

            @Override
            public void progress(List<Integer> changes) {

            }
        };
    }

    // Sets the list of all available flags as the options for the "Set flag" SuggestionField
    private void updateFlagsSuggestionList() {
        Set<String> flags = new HashSet<>();
        for (ImapDemoMessage message : getImapDemoMessageList()) {
            String a = message.getFlagsList();
            String b = a.replace("[", "");
            String c = b.replace("]", "");
            String d = c.replace(" ", "");
            String[] flagsArray = d.split(",");
            Collections.addAll(flags, flagsArray);
        }
        flagField.setSearchExecutor(searchExecutor(flags));
    }

    // Sets the list of all available folders as the options for the "Move to folder" SuggestionField
    private void updateFoldersSuggestionList() {
        Set<String> folders = new HashSet<>();
        for (ImapDemoMessage message : getImapDemoMessageList()) {
            folders.add(message.getFolderName());
        }
        folderField.setSearchExecutor(searchExecutor(folders));
    }

    private SuggestionField.SearchExecutor<String> searchExecutor(Collection<String> source) {
        return (searchString, searchParams) -> {
            List<String> suggested = source.stream()
                    .filter(str -> StringUtils.containsIgnoreCase(str, searchString))
                    .collect(Collectors.toList());
            return suggested.isEmpty() ? Collections.singletonList(searchString) : suggested;
        };
    }

    // Returns the list of all messages from all mailboxes
    private List<ImapDemoMessage> getImapDemoMessageList() {
        return dataManager.load(ImapDemoMessage.class)
                .query("select m from imapsample$ImapDemoMessage m where m.flagsList is not null")
                .view("_local")
                .list();
    }
}