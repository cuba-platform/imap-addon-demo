package com.haulmont.components.samples.imap.service;


import com.haulmont.components.imap.events.EmailFlagChangedEvent;

public interface ModifiedMessageService {
    String NAME = "imapsample_ModifiedMessageService";

    void handleEvent(EmailFlagChangedEvent event);
}