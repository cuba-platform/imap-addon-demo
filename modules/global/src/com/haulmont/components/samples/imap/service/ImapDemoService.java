package com.haulmont.components.samples.imap.service;

import com.haulmont.addon.imap.api.ImapFlag;
import com.haulmont.addon.imap.dto.ImapMessageDto;
import com.haulmont.addon.imap.entity.ImapMessage;
import com.haulmont.addon.imap.entity.ImapMessageAttachment;

import javax.mail.MessagingException;
import java.util.Collection;

public interface ImapDemoService {
    String NAME = "imapsample_ImapDemoService";

    ImapMessageDto fetchMessage(ImapMessage message) throws MessagingException;
    Collection<ImapMessageAttachment> getAttachments(ImapMessage msg) throws MessagingException;
    byte[] loadAttachment(ImapMessageAttachment attachment) throws MessagingException;

    void moveMessage(ImapMessage msg, String folderName) throws MessagingException;
    void deleteMessage(ImapMessage message) throws MessagingException;
    void setFlag(ImapMessage message, ImapFlag flag, boolean set) throws MessagingException;
}