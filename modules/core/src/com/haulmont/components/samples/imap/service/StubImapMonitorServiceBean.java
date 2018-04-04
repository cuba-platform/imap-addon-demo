package com.haulmont.components.samples.imap.service;

import com.haulmont.addon.imap.events.EmailDeletedImapEvent;
import com.haulmont.addon.imap.events.EmailFlagChangedImapEvent;
import com.haulmont.addon.imap.events.NewEmailImapEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;


@Service(ImapMonitorService.NAME)
public class StubImapMonitorServiceBean implements ImapMonitorService {
    private final static Logger log = LoggerFactory.getLogger(StubImapMonitorServiceBean.class);

    @Override
    public void onChange(EmailFlagChangedImapEvent event) {
        log.info("changed message: {}", event);
    }

    @Override
    public void onNew(NewEmailImapEvent event) {
        log.info("new message: {}", event);
    }

    @Override
    public void onDelete(EmailDeletedImapEvent event) {
        log.info("deleted message: {}", event);
    }
}