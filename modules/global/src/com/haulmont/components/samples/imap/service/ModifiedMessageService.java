package com.haulmont.components.samples.imap.service;

import com.haulmont.addon.imap.events.EmailFlagChangedImapEvent;

public interface ModifiedMessageService {
    String NAME = "imapsample_ModifiedMessageService";

    void handleEvent(EmailFlagChangedImapEvent event);
//    void handleAnyEvent(BaseImapEvent event);
    void logEvent(EmailFlagChangedImapEvent event);
}