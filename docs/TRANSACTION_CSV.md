# Transaction CSV

Use Settings → Your data → Export transactions to save a UTF-8 CSV with Android's file picker.
Use Import transactions to select that file, then confirm the import. All imported rows await review;
they do not change the balance or enter the sync queue until confirmed. Imports append rows,
skip exact duplicates (ignoring local IDs, capture links, and review status), and never overwrite
existing transactions. The whole import commits together or rolls back.

This is a ledger export, not an app backup: notification bodies, capture logs, settings, PINs,
and local database IDs are excluded. Demo mode disables import/export.

Keep these headers in this order:

```csv
title,amount_minor,currency,type,status,category,occurred_at,created_at,source_app,note,from_app,to_app
```

- `amount_minor`: positive integer centavos, e.g. `12345` means ₱123.45. No decimals or separators.
- `currency`: `PHP`.
- `type`: `INCOME`, `EXPENSE`, or `TRANSFER`.
- `status`: `CONFIRMED` or `NEEDS_REVIEW`; imports always become `NEEDS_REVIEW`.
- `title` and `category`: nonblank text.
- Dates: ISO 8601 instants, e.g. `2026-10-01T12:00:00Z`, at millisecond precision.
- App columns: optional package names. `note` is optional user-entered text.

Quoted commas, quotes, and newlines, CRLF, and a UTF-8 BOM are supported. A leading apostrophe
protects exported spreadsheet formula cells and is decoded on import. Files are limited to
5 million characters and 10,000 transaction rows. Invalid files add no transactions.

File access uses Android's [Storage Access Framework](https://developer.android.com/training/data-storage/shared/documents-files)
and requires no storage permission. iOS file import/export is unavailable until an iOS app exists.
