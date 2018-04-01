package com.haulmont.components.samples.imap.service;

import com.haulmont.addon.imap.api.ImapFlag;
import com.haulmont.addon.imap.dto.ImapMessageDto;
import com.haulmont.addon.imap.entity.ImapMessage;
import com.haulmont.addon.imap.entity.ImapMessageAttachment;

import java.util.Collection;

public interface ImapDemoService {
    String NAME = "imapsample_ImapDemoService";

    ImapMessageDto fetchMessage(ImapMessage message);
    Collection<ImapMessageAttachment> getAttachments(ImapMessage msg);
    byte[] loadAttachment(ImapMessageAttachment attachment);

    void moveMessage(ImapMessage msg, String folderName);
    void deleteMessage(ImapMessage message);
    void setFlag(ImapMessage message, ImapFlag flag, boolean set);
}