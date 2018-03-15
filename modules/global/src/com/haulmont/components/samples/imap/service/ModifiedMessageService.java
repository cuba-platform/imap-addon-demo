package com.haulmont.components.samples.imap.service;


import com.haulmont.components.imap.events.EmailFlagChangedImapEvent;

public interface ModifiedMessageService {
    String NAME = "imapsample_ModifiedMessageService";

    void handleEvent(EmailFlagChangedImapEvent event);
}