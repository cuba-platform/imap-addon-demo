package com.haulmont.components.samples.imap.service;

import com.haulmont.bali.datastruct.Pair;
import com.haulmont.components.imap.api.ImapAPI;
import com.haulmont.components.imap.api.ImapAttachmentsAPI;
import com.haulmont.components.imap.api.ImapFlag;
import com.haulmont.components.imap.dto.ImapMessageDto;
import com.haulmont.components.imap.entity.ImapMessageAttachment;
import com.haulmont.components.imap.entity.ImapMessage;
import org.springframework.stereotype.Service;

import javax.inject.Inject;
import javax.mail.MessagingException;
import java.util.ArrayList;
import java.util.Collection;

@Service(ImapDemoService.NAME)
public class ImapDemoServiceBean implements ImapDemoService {

    @Inject
    private ImapAPI imapAPI;

    @Inject
    private ImapAttachmentsAPI imapAttachmentsAPI;

    @Override
    public ImapMessageDto fetchMessage(ImapMessage message) throws MessagingException {
        return imapAPI.fetchMessage(message);
    }

    @Override
    public Collection<Pair<String, byte[]>> fetchAttachments(ImapMessage msg) throws MessagingException {
        Collection<ImapMessageAttachment> attachments = imapAPI.fetchAttachments(msg.getId());

        Collection<Pair<String, byte[]>> result = new ArrayList<>(attachments.size());
        for (ImapMessageAttachment attachment : attachments) {
            result.add(new Pair<>(attachment.getName(), imapAttachmentsAPI.loadFile(attachment)));
        }

        return result;
    }

    @Override
    public void deleteMessage(ImapMessage message) throws MessagingException {
        imapAPI.deleteMessage(message);
    }

    @Override
    public void moveMessage(ImapMessage msg, String folderName) throws MessagingException {
        imapAPI.moveMessage(msg, folderName);
    }

    @Override
    public void setFlag(ImapMessage message, ImapFlag flag, boolean set) throws MessagingException {
        imapAPI.setFlag(message, flag, set);
    }
}