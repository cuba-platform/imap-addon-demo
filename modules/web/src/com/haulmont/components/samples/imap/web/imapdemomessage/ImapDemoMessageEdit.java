package com.haulmont.components.samples.imap.web.imapdemomessage;

import com.haulmont.addon.imap.dto.ImapMessageDto;
import com.haulmont.addon.imap.entity.ImapMessage;
import com.haulmont.addon.imap.entity.ImapMessageAttachment;
import com.haulmont.components.samples.imap.service.ImapDemoService;
import com.haulmont.cuba.core.entity.Entity;
import com.haulmont.cuba.core.global.DataManager;
import com.haulmont.cuba.core.global.LoadContext;
import com.haulmont.cuba.gui.components.AbstractEditor;
import com.haulmont.components.samples.imap.entity.ImapDemoMessage;
import com.haulmont.cuba.gui.components.Label;
import com.haulmont.cuba.gui.components.Table;
import com.haulmont.cuba.gui.data.CollectionDatasource;
import com.haulmont.cuba.gui.executors.*;
import com.haulmont.cuba.gui.export.ByteArrayDataProvider;
import com.haulmont.cuba.gui.export.ExportDisplay;

import javax.inject.Inject;
import java.util.List;
import java.util.UUID;

@SuppressWarnings({"CdiInjectionPointsInspection", "SpringJavaAutowiredFieldsWarningInspection"})
public class ImapDemoMessageEdit extends AbstractEditor<ImapDemoMessage> {

    @Inject
    private ImapDemoService imapAPI;

    @Inject
    private DataManager dm;

    @Inject
    private Table<ImapMessageAttachment> attachmentsTable;

    @Inject
    private CollectionDatasource<ImapMessageAttachment, UUID> imapDemoAttachmentsDs;

    @Inject
    private Label bodyContent;

    @Inject
    private BackgroundWorker backgroundWorker;

    @Inject
    private ExportDisplay exportDisplay;

    @Override
    public void setItem(Entity item) {
        super.setItem(item);

        ImapDemoMessage msg = (ImapDemoMessage) item;
        ImapMessage imapMessage = dm.load(LoadContext.create(ImapMessage.class).setQuery(
                LoadContext.createQuery("select m from imap$Message m where m.id = :msgId")
                        .setParameter("msgId", msg.getImapMessageId()))
                .setView("imap-msg-full"));

        assert imapMessage != null;

        initBody(imapMessage);
        initAttachments(imapMessage);
    }

    public void downloadAttachment() {
        attachmentsTable.getSelected().forEach(attachment -> exportDisplay.show(
                new ByteArrayDataProvider(imapAPI.loadAttachment(attachment)),
                attachment.getName()
        ));
    }

    private void initBody(ImapMessage msg) {
        BackgroundTaskHandler taskHandler = backgroundWorker.handle(new InitBodyTask(msg));
        taskHandler.execute();
    }

    private void initAttachments(ImapMessage msg) {
        if (!Boolean.TRUE.equals(msg.getAttachmentsLoaded())) {
            BackgroundTaskHandler taskHandler = backgroundWorker.handle(new InitAttachmentTask(msg));
            taskHandler.execute();
        }
    }

    private class InitBodyTask extends BackgroundTask<Integer, String> {
        private final ImapMessage msg;

        InitBodyTask(ImapMessage msg) {
            super(0, ImapDemoMessageEdit.this);
            this.msg = msg;
        }

        @Override
        public String run(TaskLifeCycle<Integer> taskLifeCycle) {
            ImapMessageDto dto = imapAPI.fetchMessage(msg);
            return dto.getBody();

        }

        @Override
        public void canceled() {
            // Do something in UI thread if the task is canceled
        }

        @Override
        public void done(String body) {
            bodyContent.setValue(body);
        }

        @Override
        public void progress(List<Integer> changes) {
            // Show current progress in UI thread
        }
    }

    private class InitAttachmentTask extends BackgroundTask<Integer, Void> {
        private final ImapMessage msg;

        InitAttachmentTask(ImapMessage msg) {
            super(0, ImapDemoMessageEdit.this);
            this.msg = msg;
        }

        @Override
        public Void run(TaskLifeCycle<Integer> taskLifeCycle) {
            imapAPI.getAttachments(msg);
            return null;
        }

        @Override
        public void canceled() {
            // Do something in UI thread if the task is canceled
        }

        @Override
        public void done(Void _nothing) {
            imapDemoAttachmentsDs.refresh();
        }

        @Override
        public void progress(List<Integer> changes) {
            // Show current progress in UI thread
        }
    }
}