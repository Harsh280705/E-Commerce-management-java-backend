package com.ecom.level3.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ecom.level3.service.SyncService;

/** Strategy B — periodic polling bulk safety net (~every 15s, like Level 2 beat). */
@Component
public class PollingSyncScheduler {

  private final SyncService sync;

  public PollingSyncScheduler(SyncService sync) {
    this.sync = sync;
  }

  @Scheduled(fixedRateString = "${app.poll.interval-ms:15000}", initialDelayString = "20000")
  public void pollChangedOrders() {
    sync.pollChangedOrders("poll");
  }
}
