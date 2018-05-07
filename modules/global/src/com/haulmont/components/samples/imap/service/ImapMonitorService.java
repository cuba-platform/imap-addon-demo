package com.haulmont.components.samples.imap.service;


import com.haulmont.addon.imap.events.EmailDeletedImapEvent;
import com.haulmont.addon.imap.events.EmailFlagChangedImapEvent;
import com.haulmont.addon.imap.events.NewEmailImapEvent;

public interface ImapMonitorService {
    String NAME = "imapsample_ImapMonitorService";

    void onChange(EmailFlagChangedImapEvent event);
    void onNew(NewEmailImapEvent event);
    void onDelete(EmailDeletedImapEvent event);
}