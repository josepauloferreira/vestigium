package io.github.josepauloferreira.vestigium.infrastructure.persistence.stock;

import static org.assertj.core.api.Assertions.*;

import io.github.josepauloferreira.vestigium.PostgresTestConfiguration;
import io.github.josepauloferreira.vestigium.application.initialstock.DuplicateInitialStockException;
import io.github.josepauloferreira.vestigium.application.initialstock.InitialStockRepository;
import io.github.josepauloferreira.vestigium.application.item.ItemRepository;
import io.github.josepauloferreira.vestigium.application.stock.StockChronologyConflictException;
import io.github.josepauloferreira.vestigium.application.stockentry.StockEntryRepository;
import io.github.josepauloferreira.vestigium.domain.initialstock.InitialStock;
import io.github.josepauloferreira.vestigium.domain.initialstock.InitialStockQuantity;
import io.github.josepauloferreira.vestigium.domain.item.Item;
import io.github.josepauloferreira.vestigium.domain.item.ItemId;
import io.github.josepauloferreira.vestigium.domain.item.PhysicalUnit;
import io.github.josepauloferreira.vestigium.domain.stock.MovementQuantity;
import io.github.josepauloferreira.vestigium.domain.stockentry.StockEntry;
import io.github.josepauloferreira.vestigium.domain.stockentry.StockEntryId;
import io.github.josepauloferreira.vestigium.domain.stockentry.StockEntryLine;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(PostgresTestConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class StockTrackingChronologyTest {
  @Autowired InitialStockRepository stocks;
  @Autowired StockEntryRepository entries;
  @Autowired ItemRepository items;
  @Autowired PlatformTransactionManager manager;
  @Autowired JdbcTemplate jdbc;
  private final Instant start = Instant.parse("2026-01-01T08:00:00Z");

  @ParameterizedTest
  @ValueSource(longs = {-1, 0, 1})
  void openingCanPrecedeOrEqualButNotFollowExistingReceipts(long offset) {
    var id = item();
    entries.insert(receipt(start, id));
    if (offset > 0) {
      assertThatThrownBy(() -> stocks.insert(opening(id, start.plusSeconds(offset))))
          .isInstanceOf(StockChronologyConflictException.class);
      assertThat(stocks.findByItemId(id)).isEmpty();
    } else {
      stocks.insert(opening(id, start.plusSeconds(offset)));
      assertThat(stocks.findByItemId(id)).isPresent();
    }
  }

  @ParameterizedTest
  @ValueSource(longs = {-1, 0, 1})
  void newReceiptsCannotPrecedeAnOpening(long offset) {
    var id = item();
    stocks.insert(opening(id, start));
    var entry = receipt(start.plusSeconds(offset), id);
    if (offset < 0) {
      assertThatThrownBy(() -> entries.insert(entry))
          .isInstanceOf(StockChronologyConflictException.class);
      assertThat(entries.findById(entry.id())).isEmpty();
    } else {
      entries.insert(entry);
      assertThat(entries.findById(entry.id())).isPresent();
    }
  }

  @Test
  void conflictOnOneItemRejectsTheEntireReceipt() {
    var first = item();
    var second = item();
    stocks.insert(opening(second, start));
    var entry = receipt(start.minusSeconds(1), first, second);
    assertThatThrownBy(() -> entries.insert(entry))
        .isInstanceOf(StockChronologyConflictException.class);
    assertThat(entries.findById(entry.id())).isEmpty();
    assertThat(entries.findByItemId(first)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void concurrentOpeningAndEarlierReceiptSerializeAndOnlyValidFactsCommit(boolean openingFirst)
      throws Exception {
    var id = item();
    var opening = opening(id, start);
    var entry = receipt(start.minusSeconds(1), id);
    Runnable first = openingFirst ? () -> stocks.insert(opening) : () -> entries.insert(entry);
    Runnable second = openingFirst ? () -> entries.insert(entry) : () -> stocks.insert(opening);
    runBlocked(first, second, StockChronologyConflictException.class);
    assertThat(stocks.findByItemId(id).isPresent()).isEqualTo(openingFirst);
    assertThat(entries.findById(entry.id()).isPresent()).isEqualTo(!openingFirst);
  }

  @Test
  void concurrentDuplicateOpeningsPreserveTheFirstFact() throws Exception {
    var id = item();
    var first = opening(id, start);
    runBlocked(
        () -> stocks.insert(first),
        () -> stocks.insert(opening(id, start.minusSeconds(1))),
        DuplicateInitialStockException.class);
    assertThat(stocks.findByItemId(id).orElseThrow().occurredAt()).isEqualTo(start);
  }

  @Test
  void reversedMultiItemReceiptsTakeTheSameLockOrder() throws Exception {
    var a = item();
    var b = item();
    var first = receipt(start, a, b);
    var second = receipt(start, b, a);
    runBlocked(() -> entries.insert(first), () -> entries.insert(second), null);
    assertThat(entries.findByItemId(a)).hasSize(2);
  }

  private void runBlocked(Runnable first, Runnable second, Class<? extends Throwable> failure)
      throws Exception {
    var inserted = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var pid = new AtomicInteger();
    try (var pool = Executors.newFixedThreadPool(2)) {
      var owner =
          pool.submit(
              () ->
                  new TransactionTemplate(manager)
                      .executeWithoutResult(
                          tx -> {
                            first.run();
                            inserted.countDown();
                            await(release);
                          }));
      Future<?> contender = null;
      try {
        assertThat(inserted.await(10, TimeUnit.SECONDS)).isTrue();
        contender =
            pool.submit(
                () ->
                    new TransactionTemplate(manager)
                        .executeWithoutResult(
                            tx -> {
                              pid.set(
                                  jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                              second.run();
                            }));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        boolean blocked = false;
        while (System.nanoTime() < deadline) {
          if (pid.get() != 0
              && Boolean.TRUE.equals(
                  jdbc.queryForObject(
                      "SELECT EXISTS (SELECT 1 FROM pg_stat_activity WHERE pid=? AND wait_event_type='Lock')",
                      Boolean.class,
                      pid.get()))) {
            blocked = true;
            break;
          }
          Thread.sleep(10);
        }
        assertThat(blocked).as("the contender really waited on a PostgreSQL lock").isTrue();
      } finally {
        release.countDown();
      }
      owner.get(10, TimeUnit.SECONDS);
      if (failure == null) contender.get(10, TimeUnit.SECONDS);
      else {
        Future<?> result = contender;
        assertThatThrownBy(() -> result.get(10, TimeUnit.SECONDS))
            .isInstanceOf(ExecutionException.class)
            .hasCauseInstanceOf(failure);
      }
    }
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(15, TimeUnit.SECONDS)) throw new AssertionError("Release timed out");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError(e);
    }
  }

  private InitialStock opening(ItemId id, Instant time) {
    return new InitialStock(id, InitialStockQuantity.of(BigDecimal.TEN), time);
  }

  private StockEntry receipt(Instant time, ItemId... ids) {
    return new StockEntry(
        new StockEntryId(UUID.randomUUID()),
        time,
        Arrays.stream(ids)
            .map(id -> new StockEntryLine(id, new MovementQuantity(BigDecimal.ONE)))
            .toList());
  }

  private ItemId item() {
    var id = new ItemId(UUID.randomUUID());
    items.insert(new Item(id, "Synthetic chronology item", PhysicalUnit.LITRE));
    return id;
  }
}
