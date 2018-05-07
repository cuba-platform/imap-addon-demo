package com.haulmont.components.samples.imap.service;

import com.haulmont.addon.imap.api.ImapAPI;
import com.haulmont.addon.imap.api.ImapFlag;
import com.haulmont.addon.imap.dto.ImapMessageDto;
import com.haulmont.addon.imap.entity.ImapMessage;
import org.springframework.stereotype.Service;

import javax.inject.Inject;

@Service(ImapDemoService.NAME)
public class ImapDemoServiceBean implements ImapDemoService {

    private final ImapAPI imapAPI;

    @SuppressWarnings("CdiInjectionPointsInspection")
    @Inject
    public ImapDemoServiceBean(ImapAPI imapAPI) {
        this.imapAPI = imapAPI;
    }

    @Override
    public ImapMessageDto fetchMessage(ImapMessage message) {
        return imapAPI.fetchMessage(message);
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