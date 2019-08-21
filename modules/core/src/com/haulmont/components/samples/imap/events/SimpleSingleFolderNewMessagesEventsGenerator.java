package com.haulmont.components.samples.imap.events;

import com.haulmont.addon.imap.api.ImapEventsGenerator;
import com.haulmont.addon.imap.core.ImapHelper;
import com.haulmont.addon.imap.core.ImapOperations;
import com.haulmont.addon.imap.entity.ImapFolder;
import com.haulmont.addon.imap.entity.ImapMailBox;
import com.haulmont.addon.imap.entity.ImapMessage;
import com.haulmont.addon.imap.events.BaseImapEvent;
import com.haulmont.addon.imap.events.NewEmailImapEvent;
import com.haulmont.addon.imap.exception.ImapException;
import com.haulmont.bali.datastruct.Pair;
import com.haulmont.cuba.core.EntityManager;
import com.haulmont.cuba.core.Persistence;
import com.haulmont.cuba.core.Transaction;
import com.haulmont.cuba.core.global.AppBeans;
import com.haulmont.cuba.core.global.Metadata;
import com.haulmont.cuba.security.app.Authentication;
import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.IMAPMessage;
import com.sun.mail.imap.IMAPStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.event.MessageCountAdapter;
import javax.mail.event.MessageCountEvent;
import javax.mail.event.MessageCountListener;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Component("imapsample_SimpleSingleFolderNewMessagesEventsGenerator")
public class SimpleSingleFolderNewMessagesEventsGenerator implements ImapEventsGenerator {

    private final static Logger log = LoggerFactory.getLogger(SimpleSingleFolderNewMessagesEventsGenerator.class);

    private final ConcurrentMap<UUID, Pair<UUID, Set<BaseImapEvent>>> newMessagesEvents = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, ScheduledFuture<?>> standbyTasks = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Future<?>> subscriptions = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Runnable> releaseActions = new ConcurrentHashMap<>();

    private final ExecutorService executor = Executors.newCachedThreadPool(new ThreadFactory() {
        private final AtomicInteger threadNumber = new AtomicInteger(1);

        @Override
        public Thread newThread(@Nonnull Runnable r) {
            Thread thread = new Thread(
                    r, "SingleFolderNewEventsListener-" + threadNumber.getAndIncrement()
            );
            thread.setDaemon(true);
            return thread;
        }
    });
    private final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();

    private final ImapHelper imapHelper;
    private final ImapOperations imapOperations;
    private final Persistence persistence;
    private final Authentication authentication;

    @SuppressWarnings("CdiInjectionPointsInspection")
    @Inject
    public SimpleSingleFolderNewMessagesEventsGenerator(ImapHelper imapHelper,
                                                        ImapOperations imapOperations,
                                                        Persistence persistence,
                                                        Authentication authentication) {
        this.imapHelper = imapHelper;
        this.imapOperations = imapOperations;
        this.persistence = persistence;
        this.authentication = authentication;
    }

    @PreDestroy
    public void shutdownExecutor() {
        try {
            executor.shutdownNow();
        } catch (Exception e) {
            log.warn("Exception while shutting down executor", e);
        }
        try {
            scheduledExecutorService.shutdownNow();
        } catch (Exception e) {
            log.warn("Exception while shutting down scheduled executor", e);
        }
    }

    @Override
    public void init(ImapMailBox mailBox) {
        UUID mailBoxId = mailBox.getId();
        if (newMessagesEvents.containsKey(mailBoxId)) {
            return;
        }
        ImapFolder cubaFolder = singleFolder(mailBox);
        if (cubaFolder == null) {
            return;
        }
        newMessagesEvents.putIfAbsent(mailBoxId, new Pair<>(cubaFolder.getId(), new HashSet<>()));

        try {
            IMAPStore store = imapHelper.getStore(mailBox);

            IMAPFolder imapFolder = (IMAPFolder) store.getFolder(cubaFolder.getName());
            imapFolder.open(Folder.READ_ONLY);


            subscriptions.put(mailBoxId, executor.submit(() -> subscribe(cubaFolder, imapFolder)));
            releaseActions.put(mailBoxId, () -> {
                try {
                    store.close();
                } catch (MessagingException e) {
                    throw new ImapException(e);
                }
            });
        } catch (MessagingException e) {
            throw new ImapException(e);
        }
    }

    private void subscribe(ImapFolder cubaFolder, IMAPFolder imapFolder) {
        UUID mailBoxId = cubaFolder.getMailBox().getId();
        if (!newMessagesEvents.containsKey(mailBoxId)) {
            return;
        }
        MessageCountListener messageCountListener = makeListener(cubaFolder);
        try {
            imapFolder.addMessageCountListener(messageCountListener);
            standbyTasks.computeIfAbsent(mailBoxId, id ->
                    scheduledExecutorService.schedule((Runnable) imapFolder::isOpen, 2, TimeUnit.MINUTES)
            );
            imapFolder.idle();
        } catch (MessagingException e) {
            throw new ImapException(e);
        } finally {
            imapFolder.removeMessageCountListener(messageCountListener);
            ScheduledFuture<?> standbyTask = standbyTasks.get(mailBoxId);
            if (!standbyTask.isDone() && !standbyTask.isCancelled()) {
                standbyTask.cancel(false);
            }
            subscribe(cubaFolder, imapFolder);
        }
    }

    private MessageCountListener makeListener(ImapFolder folder) {
        return new MessageCountAdapter() {
            @Override
            public void messagesAdded(MessageCountEvent e) {
                if (e.getMessages().length > 0) {
                    IMAPFolder imapFolder = (IMAPFolder) e.getSource();
                    imapFolder.isOpen();
                    Pair<UUID, Set<BaseImapEvent>> folderNewEvents = newMessagesEvents.get(folder.getMailBox().getId());
                    if (folderNewEvents != null) {
                        List<BaseImapEvent> events = new ArrayList<>(e.getMessages().length);
                        MessagingException exception = null;

                        for (Message msg : e.getMessages()) {
                            authentication.begin();
                            try (Transaction tx = persistence.createTransaction()) {
                                ImapMessage imapMessage = AppBeans.get(Metadata.class).create(ImapMessage.class);
                                ImapMessage cubaMessage = imapOperations.map(imapMessage, (IMAPMessage) msg, folder);
                                EntityManager em = persistence.getEntityManager();
                                em.persist(cubaMessage);

                                tx.commit();
                                events.add(new NewEmailImapEvent(cubaMessage));
                            } catch (MessagingException e1) {
                                if (exception == null) {
                                    exception = e1;
                                } else {
                                    exception.addSuppressed(e1);
                                }
                            } finally {
                                authentication.end();
                            }

                        }

                        //noinspection SynchronizationOnLocalVariableOrMethodParameter
                        synchronized (folderNewEvents) {
                            folderNewEvents.getSecond().addAll(events);
                        }

                        if (exception != null) {
                            throw new ImapException(exception);
                        }
                    }
                }

            }
        };
    }

    @Override
    public void shutdown(ImapMailBox mailBox) {
        UUID mailBoxId = mailBox.getId();
        Pair<UUID, Set<BaseImapEvent>> mailBoxEvents = newMessagesEvents.remove(mailBoxId);
        if (mailBoxEvents == null) {
            return;
        }
        UUID folderId = mailBoxEvents.getFirst();
        log.info("unsubscribe count listener for folder#{}", folderId);

        ScheduledFuture<?> standbyTask = standbyTasks.remove(mailBoxId);
        if (standbyTask != null && !standbyTask.isCancelled() && !standbyTask.isDone()) {
            standbyTask.cancel(false);
        }

        Future<?> listenTask = subscriptions.remove(mailBoxId);
        if (listenTask != null && !listenTask.isCancelled() && !listenTask.isDone()) {
            listenTask.cancel(true);
        }

        Runnable releaseAction = releaseActions.remove(mailBoxId);
        if (releaseAction != null) {
            releaseAction.run();
        }
    }

    @Override
    public Collection<? extends BaseImapEvent> generateForNewMessages(ImapFolder folder) {
        Pair<UUID, Set<BaseImapEvent>> folderNewEvents = newMessagesEvents.get(folder.getMailBox().getId());
        if (folderNewEvents == null || !folder.getId().equals(folderNewEvents.getFirst())) {
            return Collections.emptyList();
        }
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (folderNewEvents) {
            ArrayList<BaseImapEvent> result = new ArrayList<>(folderNewEvents.getSecond());
            folderNewEvents.getSecond().clear();
            return result;
        }
    }

    @Override
    public Collection<? extends BaseImapEvent> generateForChangedMessages(ImapFolder cubaFolder) {
        return Collections.emptyList();
    }

    @Override
    public Collection<? extends BaseImapEvent> generateForMissedMessages(ImapFolder cubaFolder) {
        return Collections.emptyList();
    }

    private ImapFolder singleFolder(ImapMailBox mailBox) {
        List<ImapFolder> processableFolders = mailBox.getProcessableFolders();
        return processableFolders.isEmpty() ? null : processableFolders.get(0);
    }
}