/**
 * Compute the incremental-sync watermark for /api/sync/faces.
 *
 * The watermark must be the SERVER-side max(updated_at) of the returned rows,
 * NOT an echo of the client's `since` — clients persist this as their next
 * `since`, so an echo would never advance and force a full re-download on
 * every sync (issue #78).
 */
export function computeFacesWatermark(
  rows: Array<{ updated_at: Date }>,
  since: string | undefined | null
): string | null {
  if (rows.length > 0) {
    const maxMs = Math.max(...rows.map((r) => r.updated_at.getTime()));
    return new Date(maxMs).toISOString();
  }
  // No rows: keep the client's existing watermark (no new data, nothing to advance).
  return since || null;
}
