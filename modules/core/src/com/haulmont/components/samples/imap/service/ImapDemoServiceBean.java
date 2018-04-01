package com.haulmont.components.samples.imap.service;

import com.haulmont.addon.imap.api.ImapAPI;
import com.haulmont.addon.imap.api.ImapAttachmentsAPI;
import com.haulmont.addon.imap.api.ImapFlag;
import com.haulmont.addon.imap.dto.ImapMessageDto;
import com.haulmont.addon.imap.entity.ImapMessageAttachment;
import com.haulmont.addon.imap.entity.ImapMessage;
import org.springframework.stereotype.Service;

import javax.inject.Inject;
import java.util.Collection;

@Service(ImapDemoService.NAME)
@SuppressWarnings({"CdiInjectionPointsInspection", "SpringJavaAutowiredFieldsWarningInspection"})
public class ImapDemoServiceBean implements ImapDemoService {

    @Inject
    private ImapAPI imapAPI;

    @Inject
    private ImapAttachmentsAPI imapAttachmentsAPI;

    @Override
    public ImapMessageDto fetchMessage(ImapMessage message) {
        return imapAPI.fetchMessage(message);
    }

    @Override
    public Collection<ImapMessageAttachment> getAttachments(ImapMessage msg) {
        return imapAPI.fetchAttachments(msg.getId());
    }

    @Override
    public byte[] loadAttachment(ImapMessageAttachment attachment) {
        return imapAttachmentsAPI.loadFile(attachment);
    }

    @Override
    public void deleteMessage(ImapMessage message) {
        imapAPI.deleteMessage(message);
    }

    @Override
    public void moveMessage(ImapMessage msg, String folderName) {
        imapAPI.moveMessage(msg, folderName);
    }

    @Override
    public void setFlag(ImapMessage message, ImapFlag flag, boolean set) {
        imapAPI.setFlag(message, flag, set);
    }
}