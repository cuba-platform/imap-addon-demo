package com.haulmont.components.samples.imap.service;


import com.haulmont.bali.datastruct.Pair;
import com.haulmont.components.imap.api.ImapFlag;
import com.haulmont.components.imap.dto.ImapMessageDto;
import com.haulmont.components.imap.entity.ImapMessageRef;

import javax.mail.MessagingException;
import java.util.Collection;

public interface ImapDemoService {
    String NAME = "imapsample_ImapDemoService";

    ImapMessageDto fetchMessage(ImapMessageRef messageRef) throws MessagingException;
    Collection<Pair<String, byte[]>> fetchAttachments(ImapMessageRef msg) throws MessagingException;

    void moveMessage(ImapMessageRef msg, String folderName) throws MessagingException;
    void deleteMessage(ImapMessageRef messageRef) throws MessagingException;
    void setFlag(ImapMessageRef messageRef, ImapFlag flag, boolean set) throws MessagingException;
}