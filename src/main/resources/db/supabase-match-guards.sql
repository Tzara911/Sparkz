-- REVIEW ONLY: column names and values match the supplied Supabase schema report.
-- Inspect existing indexes and permissions before applying.
-- Never run automatically at application startup. Resolve existing duplicates first.
-- Keep this guard even when API transactions also lock the two profile rows.
create unique index match_requests_one_active_pair
    on public.match_requests (least(sender_id, receiver_id), greatest(sender_id, receiver_id))
    where status in ('pending', 'accept');
create index if not exists match_requests_incoming
    on public.match_requests(receiver_id, created_at desc);
create index if not exists match_requests_outgoing
    on public.match_requests(sender_id, created_at desc);
-- Spring must use a trusted backend DB identity. Do not expose that credential to clients.
-- Audit existing RLS policies to prevent direct client writes that bypass eligibility rules.
