package com.haulmont.components.samples.imap.service;


import com.haulmont.bali.datastruct.Pair;
import com.haulmont.components.imap.api.ImapFlag;
import com.haulmont.components.imap.dto.ImapMessageDto;
import com.haulmont.components.imap.entity.ImapMessage;

import javax.mail.MessagingException;
import java.util.Collection;

public interface ImapDemoService {
    String NAME = "imapsample_ImapDemoService";

    ImapMessageDto fetchMessage(ImapMessage message) throws MessagingException;
    Collection<Pair<String, byte[]>> fetchAttachments(ImapMessage msg) throws MessagingException;

    void moveMessage(ImapMessage msg, String folderName) throws MessagingException;
    void deleteMessage(ImapMessage message) throws MessagingException;
    void setFlag(ImapMessage message, ImapFlag flag, boolean set) throws MessagingException;
}