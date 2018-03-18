package com.haulmont.components.samples.imap.service;

import com.haulmont.bali.datastruct.Pair;
import com.haulmont.components.imap.api.ImapAPI;
import com.haulmont.components.imap.api.ImapAttachmentsAPI;
import com.haulmont.components.imap.api.ImapFlag;
import com.haulmont.components.imap.dto.ImapMessageDto;
import com.haulmont.components.imap.entity.ImapMessageAttachmentRef;
import com.haulmont.components.imap.entity.ImapMessageRef;
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
    public ImapMessageDto fetchMessage(ImapMessageRef messageRef) throws MessagingException {
        return imapAPI.fetchMessage(messageRef);
    }

    @Override
    public Collection<Pair<String, byte[]>> fetchAttachments(ImapMessageRef ref) throws MessagingException {
        Collection<ImapMessageAttachmentRef> attachmentRefs = imapAPI.fetchAttachments(ref.getId());

        Collection<Pair<String, byte[]>> result = new ArrayList<>(attachmentRefs.size());
        for (ImapMessageAttachmentRef attachmentRef : attachmentRefs) {
            result.add(new Pair<>(attachmentRef.getName(), imapAttachmentsAPI.loadFile(attachmentRef)));
        }

        return result;
    }

    @Override
    public void deleteMessage(ImapMessageRef messageRef) throws MessagingException {
        imapAPI.deleteMessage(messageRef);
    }

    @Override
    public void moveMessage(ImapMessageRef ref, String folderName) throws MessagingException {
        imapAPI.moveMessage(ref, folderName);
    }

    @Override
    public void setFlag(ImapMessageRef messageRef, ImapFlag flag, boolean set) throws MessagingException {
        imapAPI.setFlag(messageRef, flag, set);
    }
}