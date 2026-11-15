-- ============================================================================
-- VYORA Complete Supabase PostgreSQL Schema, RLS Policies, Storage & RPCs
-- Run this script in Supabase Dashboard -> SQL Editor -> New Query.
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ----------------------------------------------------------------------------
-- 1. Creator Profiles & Social Graph
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS public.vyora_profiles (
    user_id TEXT PRIMARY KEY,
    email TEXT NOT NULL DEFAULT '',
    username TEXT NOT NULL,
    display_name TEXT NOT NULL DEFAULT '',
    bio TEXT NOT NULL DEFAULT '',
    avatar_url TEXT NOT NULL DEFAULT '',
    is_private BOOLEAN NOT NULL DEFAULT FALSE,
    is_banned BOOLEAN NOT NULL DEFAULT FALSE,
    followers_count BIGINT NOT NULL DEFAULT 0 CHECK (followers_count >= 0),
    following_count BIGINT NOT NULL DEFAULT 0 CHECK (following_count >= 0),
    likes_count BIGINT NOT NULL DEFAULT 0 CHECK (likes_count >= 0),
    interests TEXT[] NOT NULL DEFAULT ARRAY['comedy', 'music', 'tech'],
    language TEXT NOT NULL DEFAULT 'ar',
    theme_mode TEXT NOT NULL DEFAULT 'dark',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Safe migrations if vyora_profiles already existed from an earlier version
ALTER TABLE public.vyora_profiles ADD COLUMN IF NOT EXISTS email TEXT NOT NULL DEFAULT '';
ALTER TABLE public.vyora_profiles ADD COLUMN IF NOT EXISTS display_name TEXT NOT NULL DEFAULT '';
ALTER TABLE public.vyora_profiles ADD COLUMN IF NOT EXISTS bio TEXT NOT NULL DEFAULT '';
ALTER TABLE public.vyora_profiles ADD COLUMN IF NOT EXISTS avatar_url TEXT NOT NULL DEFAULT '';
ALTER TABLE public.vyora_profiles ADD COLUMN IF NOT EXISTS is_private BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE public.vyora_profiles ADD COLUMN IF NOT EXISTS is_banned BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE public.vyora_profiles ADD COLUMN IF NOT EXISTS interests TEXT[] NOT NULL DEFAULT ARRAY['comedy', 'music', 'tech'];
ALTER TABLE public.vyora_profiles ADD COLUMN IF NOT EXISTS language TEXT NOT NULL DEFAULT 'ar';
ALTER TABLE public.vyora_profiles ADD COLUMN IF NOT EXISTS theme_mode TEXT NOT NULL DEFAULT 'dark';

CREATE TABLE IF NOT EXISTS public.vyora_follows (
    id TEXT PRIMARY KEY,
    follower_id TEXT NOT NULL,
    following_id TEXT NOT NULL,
    follower_username TEXT NOT NULL DEFAULT '',
    follower_avatar_url TEXT NOT NULL DEFAULT '',
    following_username TEXT NOT NULL DEFAULT '',
    following_avatar_url TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_vyora_follows_follower ON public.vyora_follows(follower_id);
CREATE INDEX IF NOT EXISTS idx_vyora_follows_following ON public.vyora_follows(following_id);

CREATE TABLE IF NOT EXISTS public.vyora_follow_requests (
    id TEXT PRIMARY KEY,
    requester_id TEXT NOT NULL,
    requester_username TEXT NOT NULL DEFAULT '',
    requester_avatar_url TEXT NOT NULL DEFAULT '',
    target_id TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'accepted', 'rejected')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_vyora_follow_req_target ON public.vyora_follow_requests(target_id, status);

CREATE TABLE IF NOT EXISTS public.vyora_blocks (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    target_id TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_vyora_blocks_user ON public.vyora_blocks(user_id);

-- ----------------------------------------------------------------------------
-- 2. Short Videos, Sounds, Hashtags & Engagement
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS public.vyora_videos_ledger (
    video_id TEXT PRIMARY KEY,
    creator_uid TEXT NOT NULL,
    creator_username TEXT NOT NULL,
    creator_avatar_url TEXT NOT NULL DEFAULT '',
    video_url TEXT NOT NULL,
    thumbnail_url TEXT NOT NULL DEFAULT '',
    caption TEXT NOT NULL DEFAULT '',
    hashtags TEXT[] NOT NULL DEFAULT '{}',
    sound_id TEXT NOT NULL DEFAULT '',
    sound_title TEXT NOT NULL DEFAULT '',
    duration_sec BIGINT NOT NULL DEFAULT 15 CHECK (duration_sec BETWEEN 1 AND 300),
    views_count BIGINT NOT NULL DEFAULT 0 CHECK (views_count >= 0),
    likes_count BIGINT NOT NULL DEFAULT 0 CHECK (likes_count >= 0),
    comments_count BIGINT NOT NULL DEFAULT 0 CHECK (comments_count >= 0),
    shares_count BIGINT NOT NULL DEFAULT 0 CHECK (shares_count >= 0),
    bookmarks_count BIGINT NOT NULL DEFAULT 0 CHECK (bookmarks_count >= 0),
    status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'removed')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Safe migrations if vyora_videos_ledger already existed from an earlier version
ALTER TABLE public.vyora_videos_ledger ADD COLUMN IF NOT EXISTS creator_avatar_url TEXT NOT NULL DEFAULT '';
ALTER TABLE public.vyora_videos_ledger ADD COLUMN IF NOT EXISTS hashtags TEXT[] NOT NULL DEFAULT '{}';
ALTER TABLE public.vyora_videos_ledger ADD COLUMN IF NOT EXISTS sound_id TEXT NOT NULL DEFAULT '';
ALTER TABLE public.vyora_videos_ledger ADD COLUMN IF NOT EXISTS comments_count BIGINT NOT NULL DEFAULT 0;
ALTER TABLE public.vyora_videos_ledger ADD COLUMN IF NOT EXISTS shares_count BIGINT NOT NULL DEFAULT 0;
ALTER TABLE public.vyora_videos_ledger ADD COLUMN IF NOT EXISTS bookmarks_count BIGINT NOT NULL DEFAULT 0;
ALTER TABLE public.vyora_videos_ledger ADD COLUMN IF NOT EXISTS content_hash TEXT NOT NULL DEFAULT '';
ALTER TABLE public.vyora_videos_ledger ADD COLUMN IF NOT EXISTS file_size_bytes BIGINT NOT NULL DEFAULT 0;
ALTER TABLE public.vyora_videos_ledger ADD COLUMN IF NOT EXISTS is_valid_for_earn BOOLEAN NOT NULL DEFAULT TRUE;

CREATE INDEX IF NOT EXISTS idx_vyora_videos_creator ON public.vyora_videos_ledger(creator_uid);
CREATE INDEX IF NOT EXISTS idx_vyora_videos_status_created ON public.vyora_videos_ledger(status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_vyora_videos_content_hash ON public.vyora_videos_ledger(content_hash) WHERE content_hash <> '';

CREATE TABLE IF NOT EXISTS public.vyora_video_likes (
    id TEXT PRIMARY KEY,
    video_id TEXT NOT NULL,
    user_id TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_vyora_likes_video ON public.vyora_video_likes(video_id);

CREATE TABLE IF NOT EXISTS public.vyora_video_bookmarks (
    id TEXT PRIMARY KEY,
    video_id TEXT NOT NULL,
    user_id TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_vyora_bookmarks_user ON public.vyora_video_bookmarks(user_id);

CREATE TABLE IF NOT EXISTS public.vyora_video_views (
    id TEXT PRIMARY KEY,
    video_id TEXT NOT NULL,
    user_id TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.vyora_video_comments (
    id TEXT PRIMARY KEY,
    video_id TEXT NOT NULL,
    user_id TEXT NOT NULL,
    username TEXT NOT NULL,
    avatar_url TEXT NOT NULL DEFAULT '',
    text TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_vyora_comments_video ON public.vyora_video_comments(video_id, created_at DESC);

CREATE TABLE IF NOT EXISTS public.vyora_sounds (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    artist TEXT NOT NULL DEFAULT '',
    audio_url TEXT NOT NULL DEFAULT '',
    cover_url TEXT NOT NULL DEFAULT '',
    duration_sec BIGINT NOT NULL DEFAULT 30,
    is_licensed BOOLEAN NOT NULL DEFAULT TRUE,
    creator_id TEXT NOT NULL DEFAULT '',
    uses_count BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.vyora_hashtags (
    id TEXT PRIMARY KEY,
    tag TEXT NOT NULL UNIQUE,
    videos_count BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ----------------------------------------------------------------------------
-- 3. Direct Messaging & Notifications
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS public.vyora_conversations (
    id TEXT PRIMARY KEY,
    participant_ids TEXT[] NOT NULL,
    last_message TEXT NOT NULL DEFAULT '',
    last_message_type TEXT NOT NULL DEFAULT 'text',
    last_sender_id TEXT NOT NULL DEFAULT '',
    last_message_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_vyora_convs_participants ON public.vyora_conversations USING GIN (participant_ids);

CREATE TABLE IF NOT EXISTS public.vyora_messages (
    id TEXT PRIMARY KEY,
    conversation_id TEXT NOT NULL,
    sender_id TEXT NOT NULL,
    participant_ids TEXT[] NOT NULL,
    type TEXT NOT NULL DEFAULT 'text',
    text TEXT NOT NULL DEFAULT '',
    media_url TEXT NOT NULL DEFAULT '',
    audio_duration_ms BIGINT NOT NULL DEFAULT 0,
    read_by TEXT[] NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_vyora_messages_conv ON public.vyora_messages(conversation_id, created_at ASC);

CREATE TABLE IF NOT EXISTS public.vyora_notifications (
    id TEXT PRIMARY KEY,
    recipient_id TEXT NOT NULL,
    actor_id TEXT NOT NULL,
    actor_username TEXT NOT NULL DEFAULT '',
    actor_avatar_url TEXT NOT NULL DEFAULT '',
    type TEXT NOT NULL DEFAULT 'like',
    target_id TEXT NOT NULL DEFAULT '',
    message TEXT NOT NULL DEFAULT '',
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_vyora_notifications_recipient ON public.vyora_notifications(recipient_id, created_at DESC);

-- ----------------------------------------------------------------------------
-- 4. Live Streams & Moderation Reports
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS public.vyora_live_streams (
    id TEXT PRIMARY KEY,
    host_id TEXT NOT NULL,
    host_username TEXT NOT NULL DEFAULT '',
    host_avatar_url TEXT NOT NULL DEFAULT '',
    channel_name TEXT NOT NULL,
    title TEXT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    viewer_count BIGINT NOT NULL DEFAULT 1,
    likes_count BIGINT NOT NULL DEFAULT 0,
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ended_at TIMESTAMPTZ
);

-- Safe migrations if vyora_live_streams already existed from an earlier version
ALTER TABLE public.vyora_live_streams ADD COLUMN IF NOT EXISTS host_id TEXT NOT NULL DEFAULT '';
ALTER TABLE public.vyora_live_streams ADD COLUMN IF NOT EXISTS host_username TEXT NOT NULL DEFAULT '';
ALTER TABLE public.vyora_live_streams ADD COLUMN IF NOT EXISTS host_avatar_url TEXT NOT NULL DEFAULT '';
ALTER TABLE public.vyora_live_streams ADD COLUMN IF NOT EXISTS channel_name TEXT NOT NULL DEFAULT '';
ALTER TABLE public.vyora_live_streams ADD COLUMN IF NOT EXISTS title TEXT NOT NULL DEFAULT 'LIVE';
ALTER TABLE public.vyora_live_streams ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE public.vyora_live_streams ADD COLUMN IF NOT EXISTS viewer_count BIGINT NOT NULL DEFAULT 1;
ALTER TABLE public.vyora_live_streams ADD COLUMN IF NOT EXISTS likes_count BIGINT NOT NULL DEFAULT 0;
ALTER TABLE public.vyora_live_streams ADD COLUMN IF NOT EXISTS started_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE public.vyora_live_streams ADD COLUMN IF NOT EXISTS ended_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_vyora_live_active ON public.vyora_live_streams(is_active, viewer_count DESC);

CREATE TABLE IF NOT EXISTS public.vyora_live_comments (
    id TEXT PRIMARY KEY,
    stream_id TEXT NOT NULL,
    user_id TEXT NOT NULL,
    username TEXT NOT NULL DEFAULT '',
    text TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_vyora_live_comments_stream ON public.vyora_live_comments(stream_id, created_at ASC);

CREATE TABLE IF NOT EXISTS public.vyora_reports (
    id TEXT PRIMARY KEY,
    reporter_id TEXT NOT NULL,
    target_type TEXT NOT NULL DEFAULT 'video',
    target_id TEXT NOT NULL,
    reason TEXT NOT NULL,
    details TEXT NOT NULL DEFAULT '',
    status TEXT NOT NULL DEFAULT 'pending',
    reviewed_by TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ----------------------------------------------------------------------------
-- 5. Wallets, Accounting Ledger, Withdrawals & Admin Config
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS public.vyora_wallets (
    user_id TEXT PRIMARY KEY,
    points BIGINT NOT NULL DEFAULT 0 CHECK (points >= 0),
    balance_usd NUMERIC(12, 4) NOT NULL DEFAULT 0 CHECK (balance_usd >= 0),
    total_earned_usd NUMERIC(12, 4) NOT NULL DEFAULT 0 CHECK (total_earned_usd >= 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.vyora_wallet_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    external_ref TEXT UNIQUE,
    user_id TEXT NOT NULL,
    tx_type TEXT NOT NULL CHECK (tx_type IN ('view_reward', 'watch_wheel', 'creator_bonus', 'withdrawal', 'adjustment')),
    points_delta BIGINT NOT NULL,
    amount_delta NUMERIC(12, 4) NOT NULL,
    description TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'completed' CHECK (status IN ('completed', 'pending', 'rejected')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
ALTER TABLE public.vyora_wallet_transactions DROP CONSTRAINT IF EXISTS vyora_wallet_transactions_tx_type_check;
ALTER TABLE public.vyora_wallet_transactions ADD CONSTRAINT vyora_wallet_transactions_tx_type_check
    CHECK (tx_type IN ('view_reward', 'watch_wheel', 'creator_bonus', 'withdrawal', 'adjustment'));
CREATE INDEX IF NOT EXISTS idx_vyora_wallet_tx_user_created ON public.vyora_wallet_transactions(user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS public.vyora_withdrawal_requests (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    amount_usd NUMERIC(12, 4) NOT NULL CHECK (amount_usd > 0),
    points_spent BIGINT NOT NULL CHECK (points_spent > 0),
    method TEXT NOT NULL,
    account_details TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'approved', 'rejected')),
    reviewed_by TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_vyora_withdrawals_user_status ON public.vyora_withdrawal_requests(user_id, status, created_at DESC);

CREATE TABLE IF NOT EXISTS public.vyora_admin_settings (
    id TEXT PRIMARY KEY DEFAULT 'global',
    point_value_usd DOUBLE PRECISION NOT NULL DEFAULT 0.01,
    min_withdrawal_usd DOUBLE PRECISION NOT NULL DEFAULT 10.0,
    rewards_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    max_video_duration_sec BIGINT NOT NULL DEFAULT 180,
    watch_wheel_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    watch_wheel_cycle_sec BIGINT NOT NULL DEFAULT 30,
    watch_wheel_points_per_cycle BIGINT NOT NULL DEFAULT 5,
    watch_wheel_daily_cap_cycles BIGINT NOT NULL DEFAULT 60,
    updated_by TEXT NOT NULL DEFAULT '',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE public.vyora_admin_settings ADD COLUMN IF NOT EXISTS watch_wheel_enabled BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE public.vyora_admin_settings ADD COLUMN IF NOT EXISTS watch_wheel_cycle_sec BIGINT NOT NULL DEFAULT 30;
ALTER TABLE public.vyora_admin_settings ADD COLUMN IF NOT EXISTS watch_wheel_points_per_cycle BIGINT NOT NULL DEFAULT 5;
ALTER TABLE public.vyora_admin_settings ADD COLUMN IF NOT EXISTS watch_wheel_daily_cap_cycles BIGINT NOT NULL DEFAULT 60;

INSERT INTO public.vyora_admin_settings (id) VALUES ('global') ON CONFLICT (id) DO NOTHING;

CREATE TABLE IF NOT EXISTS public.vyora_watch_wheel_cycles (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    cairo_date DATE NOT NULL,
    cycle_index BIGINT NOT NULL CHECK (cycle_index >= 1),
    video_id TEXT NOT NULL DEFAULT '',
    points_awarded BIGINT NOT NULL DEFAULT 0 CHECK (points_awarded >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, cairo_date, cycle_index)
);
CREATE INDEX IF NOT EXISTS idx_vyora_watch_wheel_user_date
    ON public.vyora_watch_wheel_cycles(user_id, cairo_date DESC, created_at DESC);
ALTER TABLE public.vyora_watch_wheel_cycles ENABLE ROW LEVEL SECURITY;

CREATE TABLE IF NOT EXISTS public.vyora_admin_roles (
    user_id TEXT PRIMARY KEY,
    email TEXT NOT NULL DEFAULT '',
    role TEXT NOT NULL DEFAULT 'moderator' CHECK (role IN ('moderator', 'super_admin')),
    assigned_by TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================================================
-- Helper Function: Admin Check
-- ============================================================================

CREATE OR REPLACE FUNCTION public.vyora_is_admin(p_uid TEXT DEFAULT auth.uid()::text)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
    SELECT EXISTS (
        SELECT 1 FROM public.vyora_admin_roles WHERE user_id = p_uid
    )
    OR (
        p_uid = auth.uid()::text
        AND LOWER(TRIM(COALESCE(auth.jwt() ->> 'email', ''))) = 'mahmoudomran324@gmail.com'
    );
$$;

-- ============================================================================
-- Atomic PL/pgSQL RPCs Called Directly From the Android App
-- ============================================================================

CREATE OR REPLACE FUNCTION public.vyora_ensure_profile(
    p_email TEXT DEFAULT '',
    p_display_name TEXT DEFAULT '',
    p_avatar_url TEXT DEFAULT ''
)
RETURNS SETOF public.vyora_profiles
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_uid TEXT := auth.uid()::text;
    v_jwt_email TEXT := LOWER(TRIM(COALESCE(NULLIF(p_email, ''), auth.jwt() ->> 'email', '')));
    v_base_name TEXT;
    v_handle TEXT;
BEGIN
    IF v_uid IS NULL OR v_uid = '' THEN
        RAISE EXCEPTION 'Authentication required';
    END IF;

    v_base_name := COALESCE(NULLIF(TRIM(p_display_name), ''), NULLIF(SPLIT_PART(v_jwt_email, '@', 1), ''), 'vyora_creator');
    v_handle := LOWER(REGEXP_REPLACE(v_base_name, '[^a-zA-Z0-9_]', '_', 'g'));
    v_handle := TRIM(BOTH '_' FROM v_handle);
    IF v_handle = '' THEN
        v_handle := 'creator_' || LEFT(v_uid, 6);
    END IF;
    v_handle := LEFT(v_handle, 30);

    INSERT INTO public.vyora_profiles (user_id, email, username, display_name, avatar_url)
    VALUES (v_uid, COALESCE(v_jwt_email, ''), v_handle, LEFT(v_base_name, 60), COALESCE(p_avatar_url, ''))
    ON CONFLICT (user_id) DO UPDATE
    SET email = CASE WHEN vyora_profiles.email = '' THEN EXCLUDED.email ELSE vyora_profiles.email END,
        updated_at = NOW();

    INSERT INTO public.vyora_wallets (user_id, points, balance_usd, total_earned_usd)
    VALUES (v_uid, 0, 0, 0)
    ON CONFLICT (user_id) DO NOTHING;

    IF v_jwt_email = 'mahmoudomran324@gmail.com' THEN
        INSERT INTO public.vyora_admin_roles (user_id, email, role, assigned_by)
        VALUES (v_uid, v_jwt_email, 'super_admin', 'system_bootstrap')
        ON CONFLICT (user_id) DO UPDATE
        SET email = EXCLUDED.email, role = 'super_admin';
    END IF;

    RETURN QUERY SELECT * FROM public.vyora_profiles WHERE user_id = v_uid;
END;
$$;

DROP FUNCTION IF EXISTS public.vyora_publish_video(TEXT, TEXT, TEXT, TEXT[], TEXT, TEXT, BIGINT);
CREATE OR REPLACE FUNCTION public.vyora_publish_video(
    p_video_url TEXT,
    p_thumbnail_url TEXT,
    p_caption TEXT,
    p_hashtags TEXT[],
    p_sound_id TEXT,
    p_sound_title TEXT,
    p_duration_sec BIGINT,
    p_content_hash TEXT DEFAULT '',
    p_file_size_bytes BIGINT DEFAULT 0
)
RETURNS TEXT
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_uid TEXT := auth.uid()::text;
    v_profile public.vyora_profiles%ROWTYPE;
    v_video_id TEXT := REPLACE(gen_random_uuid()::text, '-', '');
    v_tag TEXT;
    v_tag_id TEXT;
    v_clean_hash TEXT := LOWER(TRIM(COALESCE(p_content_hash, '')));
    v_is_duplicate BOOLEAN := FALSE;
    v_is_valid BOOLEAN := TRUE;
BEGIN
    IF v_uid IS NULL OR v_uid = '' THEN
        RAISE EXCEPTION 'Authentication required';
    END IF;

    SELECT * INTO v_profile FROM public.vyora_profiles WHERE user_id = v_uid;

    IF v_clean_hash <> '' THEN
        SELECT EXISTS (
            SELECT 1
            FROM public.vyora_videos_ledger
            WHERE content_hash = v_clean_hash
              AND status = 'active'
        ) INTO v_is_duplicate;
    END IF;

    IF NOT v_is_duplicate AND TRIM(COALESCE(p_video_url, '')) <> '' THEN
        SELECT EXISTS (
            SELECT 1
            FROM public.vyora_videos_ledger
            WHERE video_url = p_video_url
              AND status = 'active'
        ) INTO v_is_duplicate;
    END IF;

    v_is_valid := NOT v_is_duplicate AND COALESCE(p_duration_sec, 15) >= 2;

    INSERT INTO public.vyora_videos_ledger (
        video_id, creator_uid, creator_username, creator_avatar_url,
        video_url, thumbnail_url, caption, hashtags, sound_id, sound_title,
        duration_sec, status, content_hash, file_size_bytes, is_valid_for_earn
    )
    VALUES (
        v_video_id,
        v_uid,
        COALESCE(NULLIF(v_profile.username, ''), 'creator_' || LEFT(v_uid, 6)),
        COALESCE(v_profile.avatar_url, ''),
        p_video_url,
        COALESCE(p_thumbnail_url, ''),
        LEFT(COALESCE(p_caption, ''), 500),
        COALESCE(p_hashtags, '{}'),
        COALESCE(p_sound_id, ''),
        LEFT(COALESCE(NULLIF(p_sound_title, ''), 'Original Sound'), 120),
        LEAST(300, GREATEST(1, COALESCE(p_duration_sec, 15))),
        'active',
        v_clean_hash,
        GREATEST(0, COALESCE(p_file_size_bytes, 0)),
        v_is_valid
    );

    IF p_hashtags IS NOT NULL THEN
        FOREACH v_tag IN ARRAY p_hashtags LOOP
            v_tag_id := COALESCE(NULLIF(REGEXP_REPLACE(LOWER(v_tag), '[^a-z0-9_-]', '_', 'g'), ''), 'tag');
            INSERT INTO public.vyora_hashtags (id, tag, videos_count, updated_at)
            VALUES (LEFT(v_tag_id, 64), LOWER(v_tag), 1, NOW())
            ON CONFLICT (id) DO UPDATE
            SET videos_count = public.vyora_hashtags.videos_count + 1,
                updated_at = NOW();
        END LOOP;
    END IF;

    IF COALESCE(p_sound_id, '') <> '' THEN
        UPDATE public.vyora_sounds
        SET uses_count = uses_count + 1
        WHERE id = p_sound_id;
    END IF;

    RETURN v_video_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.vyora_toggle_like(p_video_id TEXT)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_uid TEXT := auth.uid()::text;
    v_like_id TEXT := p_video_id || ':' || v_uid;
    v_exists BOOLEAN;
    v_video public.vyora_videos_ledger%ROWTYPE;
    v_actor public.vyora_profiles%ROWTYPE;
BEGIN
    IF v_uid IS NULL OR v_uid = '' THEN
        RAISE EXCEPTION 'Authentication required';
    END IF;

    SELECT * INTO v_video FROM public.vyora_videos_ledger WHERE video_id = p_video_id;
    IF NOT FOUND THEN
        RETURN FALSE;
    END IF;

    SELECT * INTO v_actor FROM public.vyora_profiles WHERE user_id = v_uid;
    SELECT EXISTS(SELECT 1 FROM public.vyora_video_likes WHERE id = v_like_id) INTO v_exists;

    IF v_exists THEN
        DELETE FROM public.vyora_video_likes WHERE id = v_like_id;
        UPDATE public.vyora_videos_ledger
        SET likes_count = GREATEST(0, likes_count - 1), updated_at = NOW()
        WHERE video_id = p_video_id;
        UPDATE public.vyora_profiles
        SET likes_count = GREATEST(0, likes_count - 1), updated_at = NOW()
        WHERE user_id = v_video.creator_uid;
        RETURN FALSE;
    ELSE
        INSERT INTO public.vyora_video_likes (id, video_id, user_id)
        VALUES (v_like_id, p_video_id, v_uid)
        ON CONFLICT (id) DO NOTHING;
        UPDATE public.vyora_videos_ledger
        SET likes_count = likes_count + 1, updated_at = NOW()
        WHERE video_id = p_video_id;
        UPDATE public.vyora_profiles
        SET likes_count = likes_count + 1, updated_at = NOW()
        WHERE user_id = v_video.creator_uid;

        IF v_video.creator_uid <> v_uid THEN
            INSERT INTO public.vyora_notifications (
                id, recipient_id, actor_id, actor_username, actor_avatar_url, type, target_id, message
            )
            VALUES (
                REPLACE(gen_random_uuid()::text, '-', ''),
                v_video.creator_uid,
                v_uid,
                COALESCE(v_actor.username, 'vyora_user'),
                COALESCE(v_actor.avatar_url, ''),
                'like',
                p_video_id,
                '@' || COALESCE(v_actor.username, 'vyora_user') || ' liked your video'
            );
        END IF;
        RETURN TRUE;
    END IF;
END;
$$;

CREATE OR REPLACE FUNCTION public.vyora_toggle_bookmark(p_video_id TEXT)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_uid TEXT := auth.uid()::text;
    v_bm_id TEXT := p_video_id || ':' || v_uid;
    v_exists BOOLEAN;
BEGIN
    IF v_uid IS NULL OR v_uid = '' THEN
        RAISE EXCEPTION 'Authentication required';
    END IF;

    SELECT EXISTS(SELECT 1 FROM public.vyora_video_bookmarks WHERE id = v_bm_id) INTO v_exists;
    IF v_exists THEN
        DELETE FROM public.vyora_video_bookmarks WHERE id = v_bm_id;
        UPDATE public.vyora_videos_ledger
        SET bookmarks_count = GREATEST(0, bookmarks_count - 1), updated_at = NOW()
        WHERE video_id = p_video_id;
        RETURN FALSE;
    ELSE
        INSERT INTO public.vyora_video_bookmarks (id, video_id, user_id)
        VALUES (v_bm_id, p_video_id, v_uid)
        ON CONFLICT (id) DO NOTHING;
        UPDATE public.vyora_videos_ledger
        SET bookmarks_count = bookmarks_count + 1, updated_at = NOW()
        WHERE video_id = p_video_id;
        RETURN TRUE;
    END IF;
END;
$$;

CREATE OR REPLACE FUNCTION public.vyora_record_view_reward(p_video_id TEXT)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_uid TEXT := auth.uid()::text;
    v_view_id TEXT := p_video_id || ':' || v_uid;
    v_video public.vyora_videos_ledger%ROWTYPE;
    v_settings public.vyora_admin_settings%ROWTYPE;
    v_rows INTEGER := 0;
    v_ref TEXT;
    v_usd NUMERIC(12, 4);
BEGIN
    IF v_uid IS NULL OR v_uid = '' THEN
        RETURN FALSE;
    END IF;

    INSERT INTO public.vyora_video_views (id, video_id, user_id)
    VALUES (v_view_id, p_video_id, v_uid)
    ON CONFLICT (id) DO NOTHING;

    GET DIAGNOSTICS v_rows = ROW_COUNT;
    IF v_rows = 0 THEN
        RETURN FALSE;
    END IF;

    UPDATE public.vyora_videos_ledger
    SET views_count = views_count + 1, updated_at = NOW()
    WHERE video_id = p_video_id
    RETURNING * INTO v_video;

    IF NOT FOUND THEN
        RETURN FALSE;
    END IF;

    SELECT * INTO v_settings FROM public.vyora_admin_settings WHERE id = 'global';
    IF COALESCE(v_settings.rewards_enabled, TRUE) AND v_video.creator_uid <> v_uid THEN
        v_usd := COALESCE(v_settings.point_value_usd, 0.01);
        v_ref := 'view:' || p_video_id || ':' || v_uid;

        INSERT INTO public.vyora_wallets (user_id, points, balance_usd, total_earned_usd)
        VALUES (v_video.creator_uid, 0, 0, 0)
        ON CONFLICT (user_id) DO NOTHING;

        INSERT INTO public.vyora_wallet_transactions (
            external_ref, user_id, tx_type, points_delta, amount_delta, description, status
        )
        VALUES (
            v_ref,
            v_video.creator_uid,
            'view_reward',
            1,
            v_usd,
            'View reward for video ' || LEFT(p_video_id, 8),
            'completed'
        )
        ON CONFLICT (external_ref) DO NOTHING;

        GET DIAGNOSTICS v_rows = ROW_COUNT;
        IF v_rows > 0 THEN
            UPDATE public.vyora_wallets
            SET points = points + 1,
                balance_usd = balance_usd + v_usd,
                total_earned_usd = total_earned_usd + v_usd,
                updated_at = NOW()
            WHERE user_id = v_video.creator_uid;
        END IF;
    END IF;

    RETURN TRUE;
END;
$$;

CREATE OR REPLACE FUNCTION public.vyora_add_comment(p_video_id TEXT, p_text TEXT)
RETURNS TEXT
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_uid TEXT := auth.uid()::text;
    v_actor public.vyora_profiles%ROWTYPE;
    v_video public.vyora_videos_ledger%ROWTYPE;
    v_comment_id TEXT := REPLACE(gen_random_uuid()::text, '-', '');
    v_clean TEXT := LEFT(TRIM(COALESCE(p_text, '')), 500);
BEGIN
    IF v_uid IS NULL OR v_uid = '' OR v_clean = '' THEN
        RAISE EXCEPTION 'Valid authenticated user and comment text required';
    END IF;

    SELECT * INTO v_actor FROM public.vyora_profiles WHERE user_id = v_uid;
    SELECT * INTO v_video FROM public.vyora_videos_ledger WHERE video_id = p_video_id;

    INSERT INTO public.vyora_video_comments (id, video_id, user_id, username, avatar_url, text)
    VALUES (
        v_comment_id, p_video_id, v_uid,
        COALESCE(v_actor.username, 'vyora_user'),
        COALESCE(v_actor.avatar_url, ''),
        v_clean
    );

    UPDATE public.vyora_videos_ledger
    SET comments_count = comments_count + 1, updated_at = NOW()
    WHERE video_id = p_video_id;

    IF FOUND AND v_video.creator_uid <> v_uid THEN
        INSERT INTO public.vyora_notifications (
            id, recipient_id, actor_id, actor_username, actor_avatar_url, type, target_id, message
        )
        VALUES (
            REPLACE(gen_random_uuid()::text, '-', ''),
            v_video.creator_uid,
            v_uid,
            COALESCE(v_actor.username, 'vyora_user'),
            COALESCE(v_actor.avatar_url, ''),
            'comment',
            p_video_id,
            '@' || COALESCE(v_actor.username, 'vyora_user') || ': ' || LEFT(v_clean, 80)
        );
    END IF;

    RETURN v_comment_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.vyora_record_share(p_video_id TEXT)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_uid TEXT := auth.uid()::text;
    v_actor public.vyora_profiles%ROWTYPE;
    v_video public.vyora_videos_ledger%ROWTYPE;
BEGIN
    IF v_uid IS NULL OR v_uid = '' THEN
        RETURN;
    END IF;

    SELECT * INTO v_actor FROM public.vyora_profiles WHERE user_id = v_uid;
    UPDATE public.vyora_videos_ledger
    SET shares_count = shares_count + 1, updated_at = NOW()
    WHERE video_id = p_video_id
    RETURNING * INTO v_video;

    IF FOUND AND v_video.creator_uid <> v_uid THEN
        INSERT INTO public.vyora_notifications (
            id, recipient_id, actor_id, actor_username, actor_avatar_url, type, target_id, message
        )
        VALUES (
            REPLACE(gen_random_uuid()::text, '-', ''),
            v_video.creator_uid,
            v_uid,
            COALESCE(v_actor.username, 'vyora_user'),
            COALESCE(v_actor.avatar_url, ''),
            'share',
            p_video_id,
            '@' || COALESCE(v_actor.username, 'vyora_user') || ' shared your video'
        );
    END IF;
END;
$$;

CREATE OR REPLACE FUNCTION public.vyora_follow_or_request(p_target_id TEXT)
RETURNS TEXT
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_uid TEXT := auth.uid()::text;
    v_actor public.vyora_profiles%ROWTYPE;
    v_target public.vyora_profiles%ROWTYPE;
    v_edge_id TEXT := v_uid || '_' || p_target_id;
    v_rows INTEGER := 0;
BEGIN
    IF v_uid IS NULL OR v_uid = '' OR v_uid = p_target_id THEN
        RAISE EXCEPTION 'Invalid follow target';
    END IF;

    SELECT * INTO v_actor FROM public.vyora_profiles WHERE user_id = v_uid;
    SELECT * INTO v_target FROM public.vyora_profiles WHERE user_id = p_target_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Target user not found';
    END IF;

    IF v_target.is_private THEN
        INSERT INTO public.vyora_follow_requests (
            id, requester_id, requester_username, requester_avatar_url, target_id, status
        )
        VALUES (
            v_edge_id, v_uid, COALESCE(v_actor.username, 'vyora_user'), COALESCE(v_actor.avatar_url, ''), p_target_id, 'pending'
        )
        ON CONFLICT (id) DO NOTHING;
        RETURN 'requested';
    ELSE
        INSERT INTO public.vyora_follows (
            id, follower_id, following_id, follower_username, follower_avatar_url, following_username, following_avatar_url
        )
        VALUES (
            v_edge_id, v_uid, p_target_id,
            COALESCE(v_actor.username, ''), COALESCE(v_actor.avatar_url, ''),
            COALESCE(v_target.username, ''), COALESCE(v_target.avatar_url, '')
        )
        ON CONFLICT (id) DO NOTHING;

        GET DIAGNOSTICS v_rows = ROW_COUNT;
        IF v_rows > 0 THEN
            UPDATE public.vyora_profiles SET following_count = following_count + 1, updated_at = NOW() WHERE user_id = v_uid;
            UPDATE public.vyora_profiles SET followers_count = followers_count + 1, updated_at = NOW() WHERE user_id = p_target_id;
            INSERT INTO public.vyora_notifications (
                id, recipient_id, actor_id, actor_username, actor_avatar_url, type, target_id, message
            )
            VALUES (
                REPLACE(gen_random_uuid()::text, '-', ''),
                p_target_id, v_uid, COALESCE(v_actor.username, 'vyora_user'), COALESCE(v_actor.avatar_url, ''),
                'follow', v_uid, '@' || COALESCE(v_actor.username, 'vyora_user') || ' started following you'
            );
        END IF;
        RETURN 'followed';
    END IF;
END;
$$;

CREATE OR REPLACE FUNCTION public.vyora_unfollow_user(p_target_id TEXT)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_uid TEXT := auth.uid()::text;
    v_edge_id TEXT := v_uid || '_' || p_target_id;
    v_rows INTEGER := 0;
BEGIN
    IF v_uid IS NULL OR v_uid = '' THEN
        RETURN;
    END IF;
    DELETE FROM public.vyora_follows WHERE id = v_edge_id;
    GET DIAGNOSTICS v_rows = ROW_COUNT;
    IF v_rows > 0 THEN
        UPDATE public.vyora_profiles SET following_count = GREATEST(0, following_count - 1), updated_at = NOW() WHERE user_id = v_uid;
        UPDATE public.vyora_profiles SET followers_count = GREATEST(0, followers_count - 1), updated_at = NOW() WHERE user_id = p_target_id;
    END IF;
END;
$$;

CREATE OR REPLACE FUNCTION public.vyora_respond_follow_request(p_request_id TEXT, p_accept BOOLEAN)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_uid TEXT := auth.uid()::text;
    v_req public.vyora_follow_requests%ROWTYPE;
    v_target public.vyora_profiles%ROWTYPE;
    v_edge_id TEXT;
    v_rows INTEGER := 0;
BEGIN
    IF v_uid IS NULL OR v_uid = '' THEN
        RAISE EXCEPTION 'Authentication required';
    END IF;

    SELECT * INTO v_req FROM public.vyora_follow_requests WHERE id = p_request_id FOR UPDATE;
    IF NOT FOUND OR v_req.target_id <> v_uid THEN
        RETURN;
    END IF;

    UPDATE public.vyora_follow_requests
    SET status = CASE WHEN p_accept THEN 'accepted' ELSE 'rejected' END, updated_at = NOW()
    WHERE id = p_request_id;

    IF p_accept THEN
        SELECT * INTO v_target FROM public.vyora_profiles WHERE user_id = v_uid;
        v_edge_id := v_req.requester_id || '_' || v_uid;
        INSERT INTO public.vyora_follows (
            id, follower_id, following_id, follower_username, follower_avatar_url, following_username, following_avatar_url
        )
        VALUES (
            v_edge_id, v_req.requester_id, v_uid,
            v_req.requester_username, v_req.requester_avatar_url,
            COALESCE(v_target.username, ''), COALESCE(v_target.avatar_url, '')
        )
        ON CONFLICT (id) DO NOTHING;

        GET DIAGNOSTICS v_rows = ROW_COUNT;
        IF v_rows > 0 THEN
            UPDATE public.vyora_profiles SET followers_count = followers_count + 1, updated_at = NOW() WHERE user_id = v_uid;
            UPDATE public.vyora_profiles SET following_count = following_count + 1, updated_at = NOW() WHERE user_id = v_req.requester_id;
        END IF;
    END IF;
END;
$$;

CREATE OR REPLACE FUNCTION public.vyora_send_message(
    p_conversation_id TEXT,
    p_participant_ids TEXT[],
    p_type TEXT,
    p_text TEXT,
    p_media_url TEXT,
    p_audio_duration_ms BIGINT
)
RETURNS TEXT
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_uid TEXT := auth.uid()::text;
    v_msg_id TEXT := REPLACE(gen_random_uuid()::text, '-', '');
    v_actor public.vyora_profiles%ROWTYPE;
    v_preview TEXT;
    v_peer TEXT;
BEGIN
    IF v_uid IS NULL OR v_uid = '' OR NOT (v_uid = ANY(p_participant_ids)) THEN
        RAISE EXCEPTION 'Unauthorized conversation write';
    END IF;

    SELECT * INTO v_actor FROM public.vyora_profiles WHERE user_id = v_uid;

    v_preview := CASE p_type
        WHEN 'image' THEN '[Photo]'
        WHEN 'video' THEN '[Video]'
        WHEN 'audio' THEN '[Voice Note]'
        ELSE LEFT(COALESCE(p_text, ''), 120)
    END;

    INSERT INTO public.vyora_conversations (
        id, participant_ids, last_message, last_message_type, last_sender_id, last_message_at, updated_at
    )
    VALUES (
        p_conversation_id, p_participant_ids, v_preview, p_type, v_uid, NOW(), NOW()
    )
    ON CONFLICT (id) DO UPDATE
    SET last_message = EXCLUDED.last_message,
        last_message_type = EXCLUDED.last_message_type,
        last_sender_id = EXCLUDED.last_sender_id,
        last_message_at = NOW(),
        updated_at = NOW();

    INSERT INTO public.vyora_messages (
        id, conversation_id, sender_id, participant_ids, type, text, media_url, audio_duration_ms, read_by
    )
    VALUES (
        v_msg_id, p_conversation_id, v_uid, p_participant_ids, p_type,
        LEFT(COALESCE(p_text, ''), 2000),
        LEFT(COALESCE(p_media_url, ''), 1500),
        LEAST(600000, GREATEST(0, COALESCE(p_audio_duration_ms, 0))),
        ARRAY[v_uid]
    );

    SELECT p INTO v_peer FROM UNNEST(p_participant_ids) AS p WHERE p <> v_uid LIMIT 1;
    IF v_peer IS NOT NULL THEN
        INSERT INTO public.vyora_notifications (
            id, recipient_id, actor_id, actor_username, actor_avatar_url, type, target_id, message
        )
        VALUES (
            REPLACE(gen_random_uuid()::text, '-', ''),
            v_peer, v_uid,
            COALESCE(v_actor.username, 'vyora_user'),
            COALESCE(v_actor.avatar_url, ''),
            'message', p_conversation_id,
            '@' || COALESCE(v_actor.username, 'vyora_user') || ': ' || v_preview
        );
    END IF;

    RETURN v_msg_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.vyora_request_withdrawal(
    p_amount_usd NUMERIC,
    p_method TEXT,
    p_account_details TEXT
)
RETURNS TEXT
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_uid TEXT := auth.uid()::text;
    v_wallet public.vyora_wallets%ROWTYPE;
    v_settings public.vyora_admin_settings%ROWTYPE;
    v_points_needed BIGINT;
    v_req_id TEXT := REPLACE(gen_random_uuid()::text, '-', '');
BEGIN
    IF v_uid IS NULL OR v_uid = '' THEN
        RAISE EXCEPTION 'Authentication required';
    END IF;

    SELECT * INTO v_settings FROM public.vyora_admin_settings WHERE id = 'global';
    IF p_amount_usd < COALESCE(v_settings.min_withdrawal_usd, 10.0) THEN
        RAISE EXCEPTION 'Amount below minimum withdrawal threshold';
    END IF;

    SELECT * INTO v_wallet FROM public.vyora_wallets WHERE user_id = v_uid FOR UPDATE;
    IF NOT FOUND OR v_wallet.balance_usd < p_amount_usd THEN
        RAISE EXCEPTION 'Insufficient wallet balance';
    END IF;

    v_points_needed := GREATEST(1, CEIL(p_amount_usd / GREATEST(0.0001, COALESCE(v_settings.point_value_usd, 0.01)))::BIGINT);

    INSERT INTO public.vyora_withdrawal_requests (
        id, user_id, amount_usd, points_spent, method, account_details, status
    )
    VALUES (
        v_req_id, v_uid, p_amount_usd, v_points_needed, LEFT(p_method, 60), LEFT(p_account_details, 200), 'pending'
    );

    RETURN v_req_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.vyora_admin_grant_points(
    p_target_uid TEXT,
    p_points_delta BIGINT,
    p_reason TEXT
)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_uid TEXT := auth.uid()::text;
    v_settings public.vyora_admin_settings%ROWTYPE;
    v_amount_delta NUMERIC(12, 4);
    v_ref TEXT := 'admin_grant:' || p_target_uid || ':' || gen_random_uuid()::text;
BEGIN
    IF NOT public.vyora_is_admin(v_uid) THEN
        RAISE EXCEPTION 'Admin role required';
    END IF;

    SELECT * INTO v_settings FROM public.vyora_admin_settings WHERE id = 'global';
    v_amount_delta := p_points_delta * COALESCE(v_settings.point_value_usd, 0.01);

    INSERT INTO public.vyora_wallets (user_id, points, balance_usd, total_earned_usd)
    VALUES (p_target_uid, 0, 0, 0)
    ON CONFLICT (user_id) DO NOTHING;

    INSERT INTO public.vyora_wallet_transactions (
        external_ref, user_id, tx_type, points_delta, amount_delta, description, status
    )
    VALUES (
        v_ref, p_target_uid, 'adjustment', p_points_delta, v_amount_delta,
        COALESCE(NULLIF(p_reason, ''), 'Admin adjustment'), 'completed'
    );

    UPDATE public.vyora_wallets
    SET points = GREATEST(0, points + p_points_delta),
        balance_usd = GREATEST(0, balance_usd + v_amount_delta),
        total_earned_usd = total_earned_usd + GREATEST(0, v_amount_delta),
        updated_at = NOW()
    WHERE user_id = p_target_uid;

    RETURN TRUE;
END;
$$;

CREATE OR REPLACE FUNCTION public.vyora_admin_review_withdrawal(
    p_request_id TEXT,
    p_approve BOOLEAN
)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_uid TEXT := auth.uid()::text;
    v_req public.vyora_withdrawal_requests%ROWTYPE;
    v_new_status TEXT := CASE WHEN p_approve THEN 'approved' ELSE 'rejected' END;
    v_tx_ref TEXT := 'withdrawal:' || p_request_id;
    v_rows INTEGER := 0;
BEGIN
    IF NOT public.vyora_is_admin(v_uid) THEN
        RAISE EXCEPTION 'Admin role required';
    END IF;

    SELECT * INTO v_req FROM public.vyora_withdrawal_requests WHERE id = p_request_id FOR UPDATE;
    IF NOT FOUND OR v_req.status <> 'pending' THEN
        RETURN FALSE;
    END IF;

    UPDATE public.vyora_withdrawal_requests
    SET status = v_new_status, reviewed_by = v_uid, updated_at = NOW()
    WHERE id = p_request_id;

    IF p_approve THEN
        INSERT INTO public.vyora_wallet_transactions (
            external_ref, user_id, tx_type, points_delta, amount_delta, description, status
        )
        VALUES (
            v_tx_ref, v_req.user_id, 'withdrawal',
            -v_req.points_spent, -v_req.amount_usd,
            'Withdrawal via ' || v_req.method, 'completed'
        )
        ON CONFLICT (external_ref) DO NOTHING;

        GET DIAGNOSTICS v_rows = ROW_COUNT;
        IF v_rows > 0 THEN
            UPDATE public.vyora_wallets
            SET points = GREATEST(0, points - v_req.points_spent),
                balance_usd = GREATEST(0, balance_usd - v_req.amount_usd),
                updated_at = NOW()
            WHERE user_id = v_req.user_id;
        END IF;
    END IF;

    RETURN TRUE;
END;
$$;

CREATE OR REPLACE FUNCTION public.vyora_get_wallet_summary()
RETURNS JSONB
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_uid TEXT := auth.uid()::text;
    v_wallet public.vyora_wallets%ROWTYPE;
    v_tx_count BIGINT := 0;
    v_vid_count BIGINT := 0;
BEGIN
    IF v_uid IS NULL OR v_uid = '' THEN
        RETURN jsonb_build_object(
            'supabaseConfigured', TRUE,
            'synced', FALSE,
            'points', 0,
            'balanceUsd', 0,
            'totalEarnedUsd', 0,
            'transactionsCount', 0,
            'videosCount', 0
        );
    END IF;

    SELECT * INTO v_wallet FROM public.vyora_wallets WHERE user_id = v_uid;
    SELECT COUNT(*) INTO v_tx_count FROM public.vyora_wallet_transactions WHERE user_id = v_uid;
    SELECT COUNT(*) INTO v_vid_count FROM public.vyora_videos_ledger WHERE status = 'active';

    RETURN jsonb_build_object(
        'supabaseConfigured', TRUE,
        'synced', TRUE,
        'points', COALESCE(v_wallet.points, 0),
        'balanceUsd', COALESCE(v_wallet.balance_usd, 0),
        'totalEarnedUsd', COALESCE(v_wallet.total_earned_usd, 0),
        'transactionsCount', v_tx_count,
        'videosCount', v_vid_count
    );
END;
$$;

CREATE OR REPLACE FUNCTION public.vyora_live_increment_stat(
    p_stream_id TEXT,
    p_viewer_delta BIGINT DEFAULT 0,
    p_like_delta BIGINT DEFAULT 0
)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
BEGIN
    IF auth.uid() IS NULL THEN
        RETURN;
    END IF;

    UPDATE public.vyora_live_streams
    SET viewer_count = GREATEST(0, viewer_count + p_viewer_delta),
        likes_count = GREATEST(0, likes_count + p_like_delta)
    WHERE id = p_stream_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.vyora_start_live_stream(
    p_title TEXT DEFAULT ''
)
RETURNS SETOF public.vyora_live_streams
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_uid TEXT := auth.uid()::text;
    v_profile public.vyora_profiles%ROWTYPE;
    v_stream_id TEXT := REPLACE(gen_random_uuid()::text, '-', '');
    v_channel_name TEXT;
    v_title TEXT;
BEGIN
    IF v_uid IS NULL OR v_uid = '' THEN
        RAISE EXCEPTION 'Authentication required';
    END IF;

    SELECT * INTO v_profile FROM public.vyora_profiles WHERE user_id = v_uid;
    v_channel_name := 'vyora_' || LEFT(v_uid, 8) || '_' || LEFT(v_stream_id, 8);
    v_title := LEFT(
        COALESCE(
            NULLIF(TRIM(p_title), ''),
            NULLIF(TRIM(v_profile.display_name), '') || ' LIVE',
            'VYORA LIVE'
        ),
        120
    );

    INSERT INTO public.vyora_live_streams (
        id, host_id, host_username, host_avatar_url, channel_name, title, is_active, viewer_count, likes_count
    )
    VALUES (
        v_stream_id,
        v_uid,
        COALESCE(NULLIF(v_profile.username, ''), 'creator_' || LEFT(v_uid, 6)),
        COALESCE(v_profile.avatar_url, ''),
        v_channel_name,
        v_title,
        TRUE,
        1,
        0
    );

    RETURN QUERY SELECT * FROM public.vyora_live_streams WHERE id = v_stream_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.vyora_update_avatar(
    p_avatar_url TEXT
)
RETURNS SETOF public.vyora_profiles
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_uid TEXT := auth.uid()::text;
    v_email TEXT := LOWER(TRIM(COALESCE(auth.jwt() ->> 'email', '')));
    v_handle TEXT := COALESCE(NULLIF(SPLIT_PART(v_email, '@', 1), ''), 'creator_' || LEFT(v_uid, 6));
    v_clean_url TEXT := LEFT(TRIM(COALESCE(p_avatar_url, '')), 1500);
BEGIN
    IF v_uid IS NULL OR v_uid = '' THEN
        RAISE EXCEPTION 'Authentication required';
    END IF;

    INSERT INTO public.vyora_profiles (user_id, email, username, display_name, avatar_url)
    VALUES (v_uid, v_email, LEFT(v_handle, 30), LEFT(v_handle, 60), v_clean_url)
    ON CONFLICT (user_id) DO UPDATE
    SET avatar_url = EXCLUDED.avatar_url,
        updated_at = NOW();

    UPDATE public.vyora_videos_ledger
    SET creator_avatar_url = v_clean_url,
        updated_at = NOW()
    WHERE creator_uid = v_uid;

    UPDATE public.vyora_video_comments
    SET avatar_url = v_clean_url
    WHERE user_id = v_uid;

    UPDATE public.vyora_live_streams
    SET host_avatar_url = v_clean_url
    WHERE host_id = v_uid;

    RETURN QUERY SELECT * FROM public.vyora_profiles WHERE user_id = v_uid;
END;
$$;

-- ============================================================================
-- 6. Earn With Us ("اربح معنا - برنامج المكافآت") Tables, Anti-Fraud & RPCs
-- ============================================================================

CREATE TABLE IF NOT EXISTS public.vyora_earn_config (
    id TEXT PRIMARY KEY DEFAULT 'global',
    is_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    reward_egp NUMERIC(12, 2) NOT NULL DEFAULT 1500.00 CHECK (reward_egp >= 0),
    reward_points BIGINT NOT NULL DEFAULT 150000 CHECK (reward_points >= 0),
    inviter_videos_required BIGINT NOT NULL DEFAULT 10 CHECK (inviter_videos_required >= 1),
    required_friends BIGINT NOT NULL DEFAULT 10 CHECK (required_friends >= 1),
    friend_days_required BIGINT NOT NULL DEFAULT 10 CHECK (friend_days_required >= 1),
    friend_daily_minutes_required BIGINT NOT NULL DEFAULT 30 CHECK (friend_daily_minutes_required >= 1),
    friend_daily_videos_required BIGINT NOT NULL DEFAULT 2 CHECK (friend_daily_videos_required >= 1),
    updated_by TEXT NOT NULL DEFAULT '',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO public.vyora_earn_config (id) VALUES ('global') ON CONFLICT (id) DO NOTHING;

CREATE TABLE IF NOT EXISTS public.vyora_device_bindings (
    device_fingerprint TEXT PRIMARY KEY,
    app_set_id TEXT NOT NULL DEFAULT '',
    hardware_id TEXT NOT NULL DEFAULT '',
    play_integrity_status TEXT NOT NULL DEFAULT 'unavailable',
    play_integrity_token_hash TEXT NOT NULL DEFAULT '',
    first_user_id TEXT NOT NULL,
    linked_user_ids TEXT[] NOT NULL DEFAULT '{}',
    is_flagged BOOLEAN NOT NULL DEFAULT FALSE,
    flag_reason TEXT NOT NULL DEFAULT '',
    admin_decision TEXT NOT NULL DEFAULT 'clear' CHECK (admin_decision IN ('clear', 'pending', 'approved_exception', 'blocked')),
    admin_note TEXT NOT NULL DEFAULT '',
    reviewed_by TEXT NOT NULL DEFAULT '',
    reviewed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_vyora_device_appset ON public.vyora_device_bindings(app_set_id) WHERE app_set_id <> '';
CREATE INDEX IF NOT EXISTS idx_vyora_device_hw ON public.vyora_device_bindings(hardware_id) WHERE hardware_id <> '';

CREATE TABLE IF NOT EXISTS public.vyora_earn_enrollments (
    user_id TEXT PRIMARY KEY,
    referral_code TEXT NOT NULL UNIQUE,
    referred_by_uid TEXT NOT NULL DEFAULT '',
    referred_by_code TEXT NOT NULL DEFAULT '',
    referral_locked_at TIMESTAMPTZ,
    device_fingerprint TEXT NOT NULL DEFAULT '',
    app_set_id TEXT NOT NULL DEFAULT '',
    play_integrity_status TEXT NOT NULL DEFAULT 'unavailable',
    is_device_eligible BOOLEAN NOT NULL DEFAULT TRUE,
    suspicious_flag BOOLEAN NOT NULL DEFAULT FALSE,
    suspicious_reason TEXT NOT NULL DEFAULT '',
    status TEXT NOT NULL DEFAULT 'in_progress' CHECK (status IN ('in_progress', 'under_review', 'eligible', 'approved', 'rejected')),
    review_note TEXT NOT NULL DEFAULT '',
    approved_by TEXT NOT NULL DEFAULT '',
    paid_out_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_vyora_earn_ref_by ON public.vyora_earn_enrollments(referred_by_uid) WHERE referred_by_uid <> '';
CREATE INDEX IF NOT EXISTS idx_vyora_earn_device ON public.vyora_earn_enrollments(device_fingerprint) WHERE device_fingerprint <> '';

CREATE TABLE IF NOT EXISTS public.vyora_earn_daily_activity (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    cairo_date DATE NOT NULL,
    active_seconds BIGINT NOT NULL DEFAULT 0 CHECK (active_seconds >= 0),
    valid_videos_count BIGINT NOT NULL DEFAULT 0 CHECK (valid_videos_count >= 0),
    qualifies BOOLEAN NOT NULL DEFAULT FALSE,
    last_heartbeat_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, cairo_date)
);
CREATE INDEX IF NOT EXISTS idx_vyora_earn_daily_user_date ON public.vyora_earn_daily_activity(user_id, cairo_date DESC);

CREATE TABLE IF NOT EXISTS public.vyora_earn_payout_logs (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL UNIQUE,
    reward_egp NUMERIC(12, 2) NOT NULL DEFAULT 0,
    reward_points BIGINT NOT NULL DEFAULT 0,
    decision TEXT NOT NULL CHECK (decision IN ('approved', 'rejected')),
    note TEXT NOT NULL DEFAULT '',
    reviewed_by TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE OR REPLACE FUNCTION public.vyora_earn_recalc_user_day(
    p_uid TEXT,
    p_cairo_date DATE
)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_cfg public.vyora_earn_config%ROWTYPE;
    v_valid_videos BIGINT := 0;
    v_row_id TEXT := p_uid || ':' || TO_CHAR(p_cairo_date, 'YYYY-MM-DD');
BEGIN
    IF p_uid IS NULL OR p_uid = '' OR p_cairo_date IS NULL THEN
        RETURN;
    END IF;

    SELECT * INTO v_cfg FROM public.vyora_earn_config WHERE id = 'global';

    SELECT COUNT(*) INTO v_valid_videos
    FROM public.vyora_videos_ledger
    WHERE creator_uid = p_uid
      AND status = 'active'
      AND is_valid_for_earn = TRUE
      AND (TIMEZONE('Africa/Cairo', created_at))::date = p_cairo_date;

    INSERT INTO public.vyora_earn_daily_activity (
        id, user_id, cairo_date, active_seconds, valid_videos_count, qualifies, updated_at
    )
    VALUES (
        v_row_id,
        p_uid,
        p_cairo_date,
        0,
        v_valid_videos,
        (0 >= COALESCE(v_cfg.friend_daily_minutes_required, 30) * 60 AND v_valid_videos >= COALESCE(v_cfg.friend_daily_videos_required, 2)),
        NOW()
    )
    ON CONFLICT (user_id, cairo_date) DO UPDATE
    SET valid_videos_count = v_valid_videos,
        qualifies = (
            public.vyora_earn_daily_activity.active_seconds >= COALESCE(v_cfg.friend_daily_minutes_required, 30) * 60
            AND v_valid_videos >= COALESCE(v_cfg.friend_daily_videos_required, 2)
        ),
        updated_at = NOW();
END;
$$;

CREATE OR REPLACE FUNCTION public.vyora_videos_ledger_earn_trigger()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        PERFORM public.vyora_earn_recalc_user_day(
            OLD.creator_uid,
            (TIMEZONE('Africa/Cairo', OLD.created_at))::date
        );
        RETURN OLD;
    ELSE
        PERFORM public.vyora_earn_recalc_user_day(
            NEW.creator_uid,
            (TIMEZONE('Africa/Cairo', NEW.created_at))::date
        );
        RETURN NEW;
    END IF;
END;
$$;

DROP TRIGGER IF EXISTS trg_vyora_videos_earn_sync ON public.vyora_videos_ledger;
CREATE TRIGGER trg_vyora_videos_earn_sync
AFTER INSERT OR UPDATE OF status, is_valid_for_earn OR DELETE
ON public.vyora_videos_ledger
FOR EACH ROW
EXECUTE FUNCTION public.vyora_videos_ledger_earn_trigger();

CREATE OR REPLACE FUNCTION public.vyora_earn_calc_friend_streak(p_friend_uid TEXT)
RETURNS BIGINT
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_cfg public.vyora_earn_config%ROWTYPE;
    v_today DATE := (TIMEZONE('Africa/Cairo', NOW()))::date;
    v_req_days BIGINT;
    v_best_streak BIGINT := 0;
    v_active_streak BIGINT := 0;
    v_prev_date DATE := NULL;
    v_cur_run BIGINT := 0;
    v_last_qual_date DATE := NULL;
    v_rec RECORD;
BEGIN
    SELECT * INTO v_cfg FROM public.vyora_earn_config WHERE id = 'global';
    v_req_days := COALESCE(v_cfg.friend_days_required, 10);

    FOR v_rec IN
        SELECT cairo_date
        FROM public.vyora_earn_daily_activity
        WHERE user_id = p_friend_uid
          AND active_seconds >= COALESCE(v_cfg.friend_daily_minutes_required, 30) * 60
          AND valid_videos_count >= COALESCE(v_cfg.friend_daily_videos_required, 2)
          AND qualifies = TRUE
        ORDER BY cairo_date ASC
    LOOP
        IF v_prev_date IS NOT NULL AND v_rec.cairo_date = v_prev_date + 1 THEN
            v_cur_run := v_cur_run + 1;
        ELSE
            v_cur_run := 1;
        END IF;
        IF v_cur_run > v_best_streak THEN
            v_best_streak := v_cur_run;
        END IF;
        v_prev_date := v_rec.cairo_date;
        v_last_qual_date := v_rec.cairo_date;
    END LOOP;

    -- Once user completes required consecutive days, keep completed credit unless a video removal broke historical streak
    IF v_best_streak >= v_req_days THEN
        RETURN v_req_days;
    END IF;

    -- Ongoing streak only stays alive if last qualifying day is today or yesterday (Cairo time)
    IF v_last_qual_date IS NOT NULL AND v_last_qual_date >= (v_today - 1) THEN
        v_active_streak := v_cur_run;
    ELSE
        v_active_streak := 0;
    END IF;

    RETURN LEAST(v_req_days, v_active_streak);
END;
$$;

CREATE OR REPLACE FUNCTION public.vyora_earn_register_device_and_code(
    p_device_fingerprint TEXT DEFAULT '',
    p_app_set_id TEXT DEFAULT '',
    p_hardware_id TEXT DEFAULT '',
    p_play_integrity_status TEXT DEFAULT 'unavailable',
    p_play_integrity_token_hash TEXT DEFAULT ''
)
RETURNS public.vyora_earn_enrollments
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_uid TEXT := auth.uid()::text;
    v_fp TEXT := LEFT(TRIM(COALESCE(p_device_fingerprint, '')), 128);
    v_app_set TEXT := LEFT(TRIM(COALESCE(p_app_set_id, '')), 128);
    v_hw TEXT := LEFT(TRIM(COALESCE(p_hardware_id, '')), 128);
    v_pi_status TEXT := LEFT(TRIM(COALESCE(p_play_integrity_status, 'unavailable')), 64);
    v_pi_hash TEXT := LEFT(TRIM(COALESCE(p_play_integrity_token_hash, '')), 128);
    v_device public.vyora_device_bindings%ROWTYPE;
    v_existing_fp TEXT;
    v_code TEXT;
    v_attempt INTEGER := 0;
    v_is_flagged BOOLEAN := FALSE;
    v_reason TEXT := '';
    v_eligible BOOLEAN := TRUE;
    v_enrollment public.vyora_earn_enrollments%ROWTYPE;
BEGIN
    IF v_uid IS NULL OR v_uid = '' THEN
        RAISE EXCEPTION 'Authentication required';
    END IF;

    IF v_fp = '' THEN
        v_fp := 'fallback_' || encode(digest(v_uid || '_unknown', 'sha256'), 'hex');
    END IF;

    -- Match device either by primary composite fingerprint OR persistent hardware_id / app_set_id
    SELECT device_fingerprint INTO v_existing_fp
    FROM public.vyora_device_bindings
    WHERE device_fingerprint = v_fp
       OR (v_hw <> '' AND hardware_id = v_hw)
       OR (v_app_set <> '' AND app_set_id = v_app_set)
    ORDER BY created_at ASC
    LIMIT 1;

    IF v_existing_fp IS NOT NULL THEN
        v_fp := v_existing_fp;
        SELECT * INTO v_device FROM public.vyora_device_bindings WHERE device_fingerprint = v_fp FOR UPDATE;

        IF NOT (v_uid = ANY(v_device.linked_user_ids)) THEN
            IF v_device.admin_decision = 'approved_exception' THEN
                v_is_flagged := v_device.is_flagged;
                v_reason := v_device.flag_reason;
                v_eligible := TRUE;
            ELSIF v_device.first_user_id <> v_uid THEN
                v_is_flagged := TRUE;
                v_reason := 'multiple_accounts_same_device';
                v_eligible := FALSE;
            END IF;

            UPDATE public.vyora_device_bindings
            SET linked_user_ids = ARRAY(SELECT DISTINCT unnest(v_device.linked_user_ids || v_uid)),
                app_set_id = COALESCE(NULLIF(v_app_set, ''), v_device.app_set_id),
                hardware_id = COALESCE(NULLIF(v_hw, ''), v_device.hardware_id),
                play_integrity_status = COALESCE(NULLIF(v_pi_status, ''), v_device.play_integrity_status),
                play_integrity_token_hash = COALESCE(NULLIF(v_pi_hash, ''), v_device.play_integrity_token_hash),
                is_flagged = v_device.is_flagged OR v_is_flagged,
                flag_reason = COALESCE(NULLIF(v_device.flag_reason, ''), v_reason),
                admin_decision = CASE
                    WHEN v_device.admin_decision IN ('approved_exception', 'blocked') THEN v_device.admin_decision
                    WHEN v_is_flagged THEN 'pending'
                    ELSE v_device.admin_decision
                END,
                updated_at = NOW()
            WHERE device_fingerprint = v_fp;
        ELSE
            v_eligible := (v_device.admin_decision <> 'blocked')
                AND (v_device.admin_decision = 'approved_exception' OR v_device.first_user_id = v_uid OR NOT v_device.is_flagged);
            v_is_flagged := v_device.is_flagged AND v_device.admin_decision <> 'approved_exception';
            v_reason := v_device.flag_reason;

            UPDATE public.vyora_device_bindings
            SET play_integrity_status = COALESCE(NULLIF(v_pi_status, ''), v_device.play_integrity_status),
                play_integrity_token_hash = COALESCE(NULLIF(v_pi_hash, ''), v_device.play_integrity_token_hash),
                updated_at = NOW()
            WHERE device_fingerprint = v_fp;
        END IF;
    ELSE
        INSERT INTO public.vyora_device_bindings (
            device_fingerprint, app_set_id, hardware_id, play_integrity_status,
            play_integrity_token_hash, first_user_id, linked_user_ids, is_flagged, admin_decision
        )
        VALUES (
            v_fp, v_app_set, v_hw, v_pi_status, v_pi_hash, v_uid, ARRAY[v_uid], FALSE, 'clear'
        );
    END IF;

    SELECT * INTO v_enrollment FROM public.vyora_earn_enrollments WHERE user_id = v_uid;
    IF NOT FOUND THEN
        LOOP
            v_attempt := v_attempt + 1;
            v_code := 'VY' || UPPER(SUBSTRING(REPLACE(gen_random_uuid()::text, '-', ''), 1, 6));
            EXIT WHEN NOT EXISTS (SELECT 1 FROM public.vyora_earn_enrollments WHERE referral_code = v_code) OR v_attempt > 10;
        END LOOP;

        INSERT INTO public.vyora_earn_enrollments (
            user_id, referral_code, device_fingerprint, app_set_id, play_integrity_status,
            is_device_eligible, suspicious_flag, suspicious_reason, status
        )
        VALUES (
            v_uid, v_code, v_fp, v_app_set, v_pi_status,
            v_eligible, v_is_flagged, v_reason,
            CASE WHEN v_is_flagged THEN 'under_review' ELSE 'in_progress' END
        )
        ON CONFLICT (user_id) DO UPDATE
        SET device_fingerprint = EXCLUDED.device_fingerprint,
            app_set_id = EXCLUDED.app_set_id,
            play_integrity_status = EXCLUDED.play_integrity_status,
            updated_at = NOW()
        RETURNING * INTO v_enrollment;
    ELSE
        UPDATE public.vyora_earn_enrollments
        SET device_fingerprint = v_fp,
            app_set_id = COALESCE(NULLIF(v_app_set, ''), app_set_id),
            play_integrity_status = v_pi_status,
            is_device_eligible = CASE
                WHEN v_device.admin_decision = 'approved_exception' THEN TRUE
                WHEN v_device.admin_decision = 'blocked' THEN FALSE
                ELSE v_eligible
            END,
            suspicious_flag = CASE
                WHEN v_device.admin_decision = 'approved_exception' THEN FALSE
                ELSE (suspicious_flag OR v_is_flagged)
            END,
            suspicious_reason = COALESCE(NULLIF(suspicious_reason, ''), v_reason),
            updated_at = NOW()
        WHERE user_id = v_uid
        RETURNING * INTO v_enrollment;
    END IF;

    RETURN v_enrollment;
END;
$$;

CREATE OR REPLACE FUNCTION public.vyora_earn_redeem_referral(
    p_code TEXT,
    p_device_fingerprint TEXT DEFAULT '',
    p_app_set_id TEXT DEFAULT '',
    p_hardware_id TEXT DEFAULT '',
    p_play_integrity_status TEXT DEFAULT 'unavailable'
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_uid TEXT := auth.uid()::text;
    v_cfg public.vyora_earn_config%ROWTYPE;
    v_me public.vyora_earn_enrollments%ROWTYPE;
    v_inviter public.vyora_earn_enrollments%ROWTYPE;
    v_clean_code TEXT := UPPER(TRIM(COALESCE(p_code, '')));
    v_same_device_with_inviter BOOLEAN := FALSE;
    v_duplicate_friend_device BOOLEAN := FALSE;
    v_flag_reason TEXT := '';
BEGIN
    IF v_uid IS NULL OR v_uid = '' THEN
        RAISE EXCEPTION 'Authentication required';
    END IF;

    SELECT * INTO v_cfg FROM public.vyora_earn_config WHERE id = 'global';
    IF NOT COALESCE(v_cfg.is_enabled, TRUE) THEN
        RAISE EXCEPTION 'برنامج المكافآت متوقف مؤقتاً من قبل الإدارة';
    END IF;

    IF v_clean_code = '' THEN
        RAISE EXCEPTION 'أدخل كود إحالة صحيح';
    END IF;

    SELECT * INTO v_me FROM public.vyora_earn_register_device_and_code(
        p_device_fingerprint, p_app_set_id, p_hardware_id, p_play_integrity_status, ''
    );

    IF COALESCE(v_me.referred_by_uid, '') <> '' THEN
        RAISE EXCEPTION 'تم استخدام كود إحالة مسبقاً لهذا الحساب (مرة واحدة فقط)';
    END IF;

    SELECT * INTO v_inviter
    FROM public.vyora_earn_enrollments
    WHERE UPPER(referral_code) = v_clean_code;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'كود الإحالة غير صحيح أو غير مسجل';
    END IF;

    IF v_inviter.user_id = v_uid THEN
        RAISE EXCEPTION 'لا يمكنك إدخال كود الإحالة الخاص بك';
    END IF;

    v_same_device_with_inviter :=
        (v_me.device_fingerprint <> '' AND v_me.device_fingerprint = v_inviter.device_fingerprint)
        OR (v_me.app_set_id <> '' AND v_me.app_set_id = v_inviter.app_set_id);

    SELECT EXISTS (
        SELECT 1
        FROM public.vyora_earn_enrollments e2
        WHERE e2.referred_by_uid = v_inviter.user_id
          AND e2.user_id <> v_uid
          AND (
              (v_me.device_fingerprint <> '' AND e2.device_fingerprint = v_me.device_fingerprint)
              OR (v_me.app_set_id <> '' AND e2.app_set_id = v_me.app_set_id)
          )
    ) INTO v_duplicate_friend_device;

    IF v_same_device_with_inviter OR v_duplicate_friend_device OR NOT v_me.is_device_eligible THEN
        v_flag_reason := CASE
            WHEN v_same_device_with_inviter THEN 'self_device_referral_attempt'
            WHEN v_duplicate_friend_device THEN 'duplicate_friend_device'
            ELSE 'multiple_accounts_same_device'
        END;

        UPDATE public.vyora_device_bindings
        SET is_flagged = TRUE,
            flag_reason = COALESCE(NULLIF(flag_reason, ''), v_flag_reason),
            admin_decision = CASE WHEN admin_decision = 'approved_exception' THEN admin_decision ELSE 'pending' END,
            updated_at = NOW()
        WHERE device_fingerprint = v_me.device_fingerprint;

        UPDATE public.vyora_earn_enrollments
        SET referred_by_uid = v_inviter.user_id,
            referred_by_code = v_inviter.referral_code,
            referral_locked_at = NOW(),
            is_device_eligible = FALSE,
            suspicious_flag = TRUE,
            suspicious_reason = v_flag_reason,
            status = 'under_review',
            updated_at = NOW()
        WHERE user_id = v_uid;

        RETURN jsonb_build_object(
            'linked', TRUE,
            'underReview', TRUE,
            'reason', v_flag_reason
        );
    END IF;

    UPDATE public.vyora_earn_enrollments
    SET referred_by_uid = v_inviter.user_id,
        referred_by_code = v_inviter.referral_code,
        referral_locked_at = NOW(),
        updated_at = NOW()
    WHERE user_id = v_uid;

    RETURN jsonb_build_object(
        'linked', TRUE,
        'underReview', FALSE,
        'inviterUid', v_inviter.user_id
    );
END;
$$;

CREATE OR REPLACE FUNCTION public.vyora_earn_heartbeat(
    p_delta_seconds BIGINT,
    p_device_fingerprint TEXT DEFAULT '',
    p_app_set_id TEXT DEFAULT '',
    p_hardware_id TEXT DEFAULT '',
    p_play_integrity_status TEXT DEFAULT 'unavailable'
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_uid TEXT := auth.uid()::text;
    v_cfg public.vyora_earn_config%ROWTYPE;
    v_today DATE := (TIMEZONE('Africa/Cairo', NOW()))::date;
    v_row_id TEXT := v_uid || ':' || TO_CHAR(v_today, 'YYYY-MM-DD');
    v_existing public.vyora_earn_daily_activity%ROWTYPE;
    v_req_delta BIGINT := LEAST(90, GREATEST(0, COALESCE(p_delta_seconds, 0)));
    v_elapsed_sec BIGINT;
    v_credited BIGINT := 0;
    v_valid_videos BIGINT := 0;
BEGIN
    IF v_uid IS NULL OR v_uid = '' THEN
        RAISE EXCEPTION 'Authentication required';
    END IF;

    PERFORM public.vyora_earn_register_device_and_code(
        p_device_fingerprint, p_app_set_id, p_hardware_id, p_play_integrity_status, ''
    );

    SELECT * INTO v_cfg FROM public.vyora_earn_config WHERE id = 'global';
    IF NOT COALESCE(v_cfg.is_enabled, TRUE) OR v_req_delta = 0 THEN
        RETURN jsonb_build_object('creditedSeconds', 0, 'cairoDate', v_today::text, 'enabled', COALESCE(v_cfg.is_enabled, TRUE));
    END IF;

    SELECT COUNT(*) INTO v_valid_videos
    FROM public.vyora_videos_ledger
    WHERE creator_uid = v_uid
      AND status = 'active'
      AND is_valid_for_earn = TRUE
      AND (TIMEZONE('Africa/Cairo', created_at))::date = v_today;

    INSERT INTO public.vyora_earn_daily_activity (
        id, user_id, cairo_date, active_seconds, valid_videos_count, qualifies, last_heartbeat_at
    )
    VALUES (v_row_id, v_uid, v_today, 0, v_valid_videos, FALSE, NULL)
    ON CONFLICT (user_id, cairo_date) DO NOTHING;

    SELECT * INTO v_existing
    FROM public.vyora_earn_daily_activity
    WHERE user_id = v_uid AND cairo_date = v_today
    FOR UPDATE;

    IF v_existing.last_heartbeat_at IS NULL THEN
        v_credited := v_req_delta;
    ELSE
        v_elapsed_sec := GREATEST(0, FLOOR(EXTRACT(EPOCH FROM (NOW() - v_existing.last_heartbeat_at)))::BIGINT);
        IF v_elapsed_sec < 10 THEN
            v_credited := 0;
        ELSE
            v_credited := LEAST(v_req_delta, v_elapsed_sec + 5);
        END IF;
    END IF;

    UPDATE public.vyora_earn_daily_activity
    SET active_seconds = active_seconds + v_credited,
        valid_videos_count = v_valid_videos,
        qualifies = (
            (active_seconds + v_credited) >= COALESCE(v_cfg.friend_daily_minutes_required, 30) * 60
            AND v_valid_videos >= COALESCE(v_cfg.friend_daily_videos_required, 2)
        ),
        last_heartbeat_at = CASE WHEN v_credited > 0 OR last_heartbeat_at IS NULL THEN NOW() ELSE last_heartbeat_at END,
        updated_at = NOW()
    WHERE id = v_existing.id
    RETURNING * INTO v_existing;

    RETURN jsonb_build_object(
        'creditedSeconds', v_credited,
        'activeSecondsToday', v_existing.active_seconds,
        'validVideosToday', v_existing.valid_videos_count,
        'qualifiesToday', v_existing.qualifies,
        'cairoDate', v_today::text,
        'enabled', TRUE
    );
END;
$$;

CREATE OR REPLACE FUNCTION public.vyora_earn_get_dashboard(
    p_device_fingerprint TEXT DEFAULT '',
    p_app_set_id TEXT DEFAULT '',
    p_hardware_id TEXT DEFAULT '',
    p_play_integrity_status TEXT DEFAULT 'unavailable'
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_uid TEXT := auth.uid()::text;
    v_cfg public.vyora_earn_config%ROWTYPE;
    v_me public.vyora_earn_enrollments%ROWTYPE;
    v_today DATE := (TIMEZONE('Africa/Cairo', NOW()))::date;
    v_personal_videos BIGINT := 0;
    v_invited_count BIGINT := 0;
    v_completed_friends BIGINT := 0;
    v_has_suspicious_friend BOOLEAN := FALSE;
    v_my_today public.vyora_earn_daily_activity%ROWTYPE;
    v_my_streak BIGINT := 0;
    v_status TEXT;
    v_friends JSONB := '[]'::jsonb;
    v_friend_rec RECORD;
    v_friend_streak BIGINT;
    v_friend_sec BIGINT;
    v_friend_vids BIGINT;
    v_friend_done BOOLEAN;
    v_progress_pct DOUBLE PRECISION;
BEGIN
    IF v_uid IS NULL OR v_uid = '' THEN
        RAISE EXCEPTION 'Authentication required';
    END IF;

    SELECT * INTO v_cfg FROM public.vyora_earn_config WHERE id = 'global';
    SELECT * INTO v_me FROM public.vyora_earn_register_device_and_code(
        p_device_fingerprint, p_app_set_id, p_hardware_id, p_play_integrity_status, ''
    );

    SELECT COUNT(*) INTO v_personal_videos
    FROM public.vyora_videos_ledger
    WHERE creator_uid = v_uid
      AND status = 'active'
      AND is_valid_for_earn = TRUE;

    SELECT * INTO v_my_today
    FROM public.vyora_earn_daily_activity
    WHERE user_id = v_uid AND cairo_date = v_today;

    v_my_streak := public.vyora_earn_calc_friend_streak(v_uid);

    FOR v_friend_rec IN
        SELECT e.user_id, e.is_device_eligible, e.suspicious_flag, e.suspicious_reason, e.created_at,
               COALESCE(p.username, 'creator_' || LEFT(e.user_id, 6)) AS username,
               COALESCE(p.display_name, COALESCE(p.username, 'Creator')) AS display_name,
               COALESCE(p.avatar_url, '') AS avatar_url
        FROM public.vyora_earn_enrollments e
        LEFT JOIN public.vyora_profiles p ON p.user_id = e.user_id
        WHERE e.referred_by_uid = v_uid
        ORDER BY e.created_at ASC
    LOOP
        v_invited_count := v_invited_count + 1;

        SELECT COALESCE(active_seconds, 0), COALESCE(valid_videos_count, 0)
        INTO v_friend_sec, v_friend_vids
        FROM public.vyora_earn_daily_activity
        WHERE user_id = v_friend_rec.user_id AND cairo_date = v_today;

        v_friend_sec := COALESCE(v_friend_sec, 0);
        v_friend_vids := COALESCE(v_friend_vids, 0);
        v_friend_streak := public.vyora_earn_calc_friend_streak(v_friend_rec.user_id);

        v_friend_done := v_friend_rec.is_device_eligible
            AND NOT v_friend_rec.suspicious_flag
            AND v_friend_streak >= COALESCE(v_cfg.friend_days_required, 10);

        IF v_friend_done THEN
            v_completed_friends := v_completed_friends + 1;
        END IF;
        IF v_friend_rec.suspicious_flag OR NOT v_friend_rec.is_device_eligible THEN
            v_has_suspicious_friend := TRUE;
        END IF;

        v_friends := v_friends || jsonb_build_object(
            'userId', v_friend_rec.user_id,
            'username', v_friend_rec.username,
            'displayName', v_friend_rec.display_name,
            'avatarUrl', v_friend_rec.avatar_url,
            'registeredAt', v_friend_rec.created_at,
            'completedDays', v_friend_streak,
            'requiredDays', COALESCE(v_cfg.friend_days_required, 10),
            'todayActiveMinutes', FLOOR(v_friend_sec / 60.0)::BIGINT,
            'todayActiveSeconds', v_friend_sec,
            'requiredDailyMinutes', COALESCE(v_cfg.friend_daily_minutes_required, 30),
            'todayValidVideos', v_friend_vids,
            'requiredDailyVideos', COALESCE(v_cfg.friend_daily_videos_required, 2),
            'isCompleted', v_friend_done,
            'isDeviceEligible', v_friend_rec.is_device_eligible,
            'suspiciousFlag', v_friend_rec.suspicious_flag,
            'suspiciousReason', v_friend_rec.suspicious_reason
        );
    END LOOP;

    v_status := v_me.status;
    IF v_status NOT IN ('approved', 'rejected') THEN
        IF v_me.suspicious_flag OR NOT v_me.is_device_eligible THEN
            v_status := 'under_review';
        ELSIF v_personal_videos >= COALESCE(v_cfg.inviter_videos_required, 10)
          AND v_completed_friends >= COALESCE(v_cfg.required_friends, 10) THEN
            v_status := CASE WHEN v_has_suspicious_friend THEN 'under_review' ELSE 'eligible' END;
        ELSE
            v_status := 'in_progress';
        END IF;

        IF v_status <> v_me.status THEN
            UPDATE public.vyora_earn_enrollments
            SET status = v_status, updated_at = NOW()
            WHERE user_id = v_uid;
        END IF;
    END IF;

    v_progress_pct := LEAST(
        100.0,
        (
            LEAST(1.0, v_personal_videos::DOUBLE PRECISION / GREATEST(1, COALESCE(v_cfg.inviter_videos_required, 10))) * 0.25
            + LEAST(1.0, v_completed_friends::DOUBLE PRECISION / GREATEST(1, COALESCE(v_cfg.required_friends, 10))) * 0.75
        ) * 100.0
    );

    RETURN jsonb_build_object(
        'config', jsonb_build_object(
            'isEnabled', COALESCE(v_cfg.is_enabled, TRUE),
            'rewardEgp', COALESCE(v_cfg.reward_egp, 1500.00),
            'rewardPoints', COALESCE(v_cfg.reward_points, 150000),
            'inviterVideosRequired', COALESCE(v_cfg.inviter_videos_required, 10),
            'requiredFriends', COALESCE(v_cfg.required_friends, 10),
            'friendDaysRequired', COALESCE(v_cfg.friend_days_required, 10),
            'friendDailyMinutesRequired', COALESCE(v_cfg.friend_daily_minutes_required, 30),
            'friendDailyVideosRequired', COALESCE(v_cfg.friend_daily_videos_required, 2)
        ),
        'referralCode', v_me.referral_code,
        'referredByCode', v_me.referred_by_code,
        'referredByUid', v_me.referred_by_uid,
        'personalVideosCount', v_personal_videos,
        'invitedFriendsCount', v_invited_count,
        'completedFriendsCount', v_completed_friends,
        'myTodayMinutes', FLOOR(COALESCE(v_my_today.active_seconds, 0) / 60.0)::BIGINT,
        'myTodayVideos', COALESCE(v_my_today.valid_videos_count, 0),
        'myConsecutiveDays', v_my_streak,
        'progressPercent', v_progress_pct,
        'status', v_status,
        'reviewNote', v_me.review_note,
        'isDeviceEligible', v_me.is_device_eligible,
        'suspiciousFlag', v_me.suspicious_flag,
        'suspiciousReason', v_me.suspicious_reason,
        'cairoDate', v_today::text,
        'friends', v_friends
    );
END;
$$;

CREATE OR REPLACE FUNCTION public.vyora_admin_update_earn_config(
    p_is_enabled BOOLEAN,
    p_reward_egp NUMERIC,
    p_reward_points BIGINT,
    p_inviter_videos_required BIGINT DEFAULT 10,
    p_required_friends BIGINT DEFAULT 10,
    p_friend_days_required BIGINT DEFAULT 10,
    p_friend_daily_minutes_required BIGINT DEFAULT 30,
    p_friend_daily_videos_required BIGINT DEFAULT 2
)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_uid TEXT := auth.uid()::text;
BEGIN
    IF NOT public.vyora_is_admin(v_uid) THEN
        RAISE EXCEPTION 'Admin role required';
    END IF;

    INSERT INTO public.vyora_earn_config (
        id, is_enabled, reward_egp, reward_points, inviter_videos_required,
        required_friends, friend_days_required, friend_daily_minutes_required,
        friend_daily_videos_required, updated_by, updated_at
    )
    VALUES (
        'global',
        COALESCE(p_is_enabled, TRUE),
        GREATEST(0, COALESCE(p_reward_egp, 1500)),
        GREATEST(0, COALESCE(p_reward_points, 150000)),
        GREATEST(1, COALESCE(p_inviter_videos_required, 10)),
        GREATEST(1, COALESCE(p_required_friends, 10)),
        GREATEST(1, COALESCE(p_friend_days_required, 10)),
        GREATEST(1, COALESCE(p_friend_daily_minutes_required, 30)),
        GREATEST(1, COALESCE(p_friend_daily_videos_required, 2)),
        v_uid,
        NOW()
    )
    ON CONFLICT (id) DO UPDATE
    SET is_enabled = EXCLUDED.is_enabled,
        reward_egp = EXCLUDED.reward_egp,
        reward_points = EXCLUDED.reward_points,
        inviter_videos_required = EXCLUDED.inviter_videos_required,
        required_friends = EXCLUDED.required_friends,
        friend_days_required = EXCLUDED.friend_days_required,
        friend_daily_minutes_required = EXCLUDED.friend_daily_minutes_required,
        friend_daily_videos_required = EXCLUDED.friend_daily_videos_required,
        updated_by = v_uid,
        updated_at = NOW();

    RETURN TRUE;
END;
$$;

CREATE OR REPLACE FUNCTION public.vyora_admin_review_device(
    p_device_fingerprint TEXT,
    p_decision TEXT,
    p_note TEXT DEFAULT ''
)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_uid TEXT := auth.uid()::text;
    v_dev public.vyora_device_bindings%ROWTYPE;
BEGIN
    IF NOT public.vyora_is_admin(v_uid) THEN
        RAISE EXCEPTION 'Admin role required';
    END IF;

    IF p_decision NOT IN ('approved_exception', 'blocked', 'pending', 'clear') THEN
        RAISE EXCEPTION 'Invalid device review decision';
    END IF;

    SELECT * INTO v_dev FROM public.vyora_device_bindings WHERE device_fingerprint = p_device_fingerprint FOR UPDATE;
    IF NOT FOUND THEN
        RETURN FALSE;
    END IF;

    UPDATE public.vyora_device_bindings
    SET admin_decision = p_decision,
        is_flagged = CASE WHEN p_decision IN ('approved_exception', 'clear') THEN FALSE ELSE TRUE END,
        admin_note = LEFT(COALESCE(p_note, ''), 300),
        reviewed_by = v_uid,
        reviewed_at = NOW(),
        updated_at = NOW()
    WHERE device_fingerprint = p_device_fingerprint;

    IF p_decision IN ('approved_exception', 'clear') THEN
        UPDATE public.vyora_earn_enrollments
        SET is_device_eligible = TRUE,
            suspicious_flag = FALSE,
            review_note = LEFT(COALESCE(p_note, ''), 300),
            status = CASE WHEN status = 'under_review' THEN 'in_progress' ELSE status END,
            updated_at = NOW()
        WHERE device_fingerprint = p_device_fingerprint;
    ELSIF p_decision = 'blocked' THEN
        UPDATE public.vyora_earn_enrollments
        SET is_device_eligible = FALSE,
            suspicious_flag = TRUE,
            review_note = LEFT(COALESCE(p_note, ''), 300),
            status = CASE WHEN status <> 'approved' THEN 'rejected' ELSE status END,
            updated_at = NOW()
        WHERE device_fingerprint = p_device_fingerprint;
    END IF;

    RETURN TRUE;
END;
$$;

CREATE OR REPLACE FUNCTION public.vyora_admin_review_earn_payout(
    p_target_uid TEXT,
    p_approve BOOLEAN,
    p_note TEXT DEFAULT ''
)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_admin_uid TEXT := auth.uid()::text;
    v_cfg public.vyora_earn_config%ROWTYPE;
    v_settings public.vyora_admin_settings%ROWTYPE;
    v_enroll public.vyora_earn_enrollments%ROWTYPE;
    v_tx_ref TEXT := 'earn_reward:' || p_target_uid;
    v_amount_usd NUMERIC(12, 4);
    v_rows INTEGER := 0;
    v_clean_note TEXT := LEFT(TRIM(COALESCE(p_note, '')), 300);
BEGIN
    IF NOT public.vyora_is_admin(v_admin_uid) THEN
        RAISE EXCEPTION 'Admin role required';
    END IF;

    SELECT * INTO v_enroll FROM public.vyora_earn_enrollments WHERE user_id = p_target_uid FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Enrollment not found';
    END IF;

    IF v_enroll.status = 'approved' OR v_enroll.paid_out_at IS NOT NULL THEN
        RAISE EXCEPTION 'تم صرف هذه المكافأة للمستخدم مسبقاً ولا يمكن تكرار الصرف';
    END IF;

    SELECT * INTO v_cfg FROM public.vyora_earn_config WHERE id = 'global';
    SELECT * INTO v_settings FROM public.vyora_admin_settings WHERE id = 'global';

    IF p_approve THEN
        v_amount_usd := COALESCE(v_cfg.reward_points, 150000) * COALESCE(v_settings.point_value_usd, 0.01);

        INSERT INTO public.vyora_wallets (user_id, points, balance_usd, total_earned_usd)
        VALUES (p_target_uid, 0, 0, 0)
        ON CONFLICT (user_id) DO NOTHING;

        INSERT INTO public.vyora_wallet_transactions (
            external_ref, user_id, tx_type, points_delta, amount_delta, description, status
        )
        VALUES (
            v_tx_ref,
            p_target_uid,
            'creator_bonus',
            COALESCE(v_cfg.reward_points, 150000),
            v_amount_usd,
            'مكافأة برنامج اربح معنا (' || COALESCE(v_cfg.reward_egp, 1500)::TEXT || ' ج.م)',
            'completed'
        )
        ON CONFLICT (external_ref) DO NOTHING;

        GET DIAGNOSTICS v_rows = ROW_COUNT;
        IF v_rows = 0 THEN
            RAISE EXCEPTION 'تم صرف مكافأة هذا المستخدم مسبقاً في سجل المعاملات';
        END IF;

        UPDATE public.vyora_wallets
        SET points = points + COALESCE(v_cfg.reward_points, 150000),
            balance_usd = balance_usd + v_amount_usd,
            total_earned_usd = total_earned_usd + v_amount_usd,
            updated_at = NOW()
        WHERE user_id = p_target_uid;

        UPDATE public.vyora_earn_enrollments
        SET status = 'approved',
            review_note = COALESCE(NULLIF(v_clean_note, ''), 'تم اعتماد مكافأة برنامج اربح معنا'),
            approved_by = v_admin_uid,
            paid_out_at = NOW(),
            updated_at = NOW()
        WHERE user_id = p_target_uid;

        INSERT INTO public.vyora_earn_payout_logs (
            id, user_id, reward_egp, reward_points, decision, note, reviewed_by
        )
        VALUES (
            REPLACE(gen_random_uuid()::text, '-', ''),
            p_target_uid,
            COALESCE(v_cfg.reward_egp, 1500),
            COALESCE(v_cfg.reward_points, 150000),
            'approved',
            COALESCE(NULLIF(v_clean_note, ''), 'تم اعتماد المكافأة'),
            v_admin_uid
        )
        ON CONFLICT (user_id) DO NOTHING;

        INSERT INTO public.vyora_notifications (
            id, recipient_id, actor_id, actor_username, type, target_id, message
        )
        VALUES (
            REPLACE(gen_random_uuid()::text, '-', ''),
            p_target_uid,
            v_admin_uid,
            'VYORA',
            'creator_bonus',
            p_target_uid,
            'مبروك! تم اعتماد مكافأة اربح معنا وإضافة ' || COALESCE(v_cfg.reward_points, 150000)::TEXT || ' نقطة (تعادل ' || COALESCE(v_cfg.reward_egp, 1500)::TEXT || ' ج.م) إلى محفظتك'
        );
    ELSE
        UPDATE public.vyora_earn_enrollments
        SET status = 'rejected',
            review_note = COALESCE(NULLIF(v_clean_note, ''), 'تم رفض الطلب لعدم استيفاء الشروط'),
            approved_by = v_admin_uid,
            updated_at = NOW()
        WHERE user_id = p_target_uid;

        INSERT INTO public.vyora_earn_payout_logs (
            id, user_id, reward_egp, reward_points, decision, note, reviewed_by
        )
        VALUES (
            REPLACE(gen_random_uuid()::text, '-', ''),
            p_target_uid,
            COALESCE(v_cfg.reward_egp, 1500),
            COALESCE(v_cfg.reward_points, 150000),
            'rejected',
            COALESCE(NULLIF(v_clean_note, ''), 'لم يتم استيفاء الشروط'),
            v_admin_uid
        )
        ON CONFLICT (user_id) DO UPDATE
        SET decision = 'rejected',
            note = EXCLUDED.note,
            reviewed_by = v_admin_uid,
            created_at = NOW();

        INSERT INTO public.vyora_notifications (
            id, recipient_id, actor_id, actor_username, type, target_id, message
        )
        VALUES (
            REPLACE(gen_random_uuid()::text, '-', ''),
            p_target_uid,
            v_admin_uid,
            'VYORA',
            'creator_bonus',
            p_target_uid,
            'تم رفض طلب مكافأة اربح معنا: ' || COALESCE(NULLIF(v_clean_note, ''), 'مخالفة شروط البرنامج')
        );
    END IF;

    RETURN TRUE;
END;
$$;

CREATE OR REPLACE FUNCTION public.vyora_admin_get_earn_overview()
RETURNS JSONB
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_uid TEXT := auth.uid()::text;
    v_cfg public.vyora_earn_config%ROWTYPE;
    v_today DATE := (TIMEZONE('Africa/Cairo', NOW()))::date;
    v_participants JSONB := '[]'::jsonb;
    v_devices JSONB := '[]'::jsonb;
    v_logs JSONB := '[]'::jsonb;
    v_p RECORD;
    v_f RECORD;
    v_friends JSONB;
    v_vids BIGINT;
    v_invited BIGINT;
    v_completed BIGINT;
    v_f_sec BIGINT;
    v_f_vids BIGINT;
    v_f_streak BIGINT;
    v_f_done BOOLEAN;
BEGIN
    IF NOT public.vyora_is_admin(v_uid) THEN
        RAISE EXCEPTION 'Admin role required';
    END IF;

    SELECT * INTO v_cfg FROM public.vyora_earn_config WHERE id = 'global';

    FOR v_p IN
        SELECT e.*,
               COALESCE(p.username, 'creator_' || LEFT(e.user_id, 6)) AS username,
               COALESCE(p.display_name, COALESCE(p.username, 'Creator')) AS display_name,
               COALESCE(p.email, '') AS email,
               COALESCE(p.avatar_url, '') AS avatar_url
        FROM public.vyora_earn_enrollments e
        LEFT JOIN public.vyora_profiles p ON p.user_id = e.user_id
        ORDER BY
            CASE e.status
                WHEN 'eligible' THEN 1
                WHEN 'under_review' THEN 2
                WHEN 'in_progress' THEN 3
                WHEN 'approved' THEN 4
                ELSE 5
            END,
            e.created_at DESC
        LIMIT 100
    LOOP
        SELECT COUNT(*) INTO v_vids
        FROM public.vyora_videos_ledger
        WHERE creator_uid = v_p.user_id AND status = 'active' AND is_valid_for_earn = TRUE;

        v_invited := 0;
        v_completed := 0;
        v_friends := '[]'::jsonb;

        FOR v_f IN
            SELECT fe.user_id, fe.is_device_eligible, fe.suspicious_flag, fe.suspicious_reason, fe.created_at,
                   COALESCE(fp.username, 'creator_' || LEFT(fe.user_id, 6)) AS username,
                   COALESCE(fp.display_name, COALESCE(fp.username, 'Creator')) AS display_name,
                   COALESCE(fp.avatar_url, '') AS avatar_url
            FROM public.vyora_earn_enrollments fe
            LEFT JOIN public.vyora_profiles fp ON fp.user_id = fe.user_id
            WHERE fe.referred_by_uid = v_p.user_id
            ORDER BY fe.created_at ASC
        LOOP
            v_invited := v_invited + 1;
            SELECT COALESCE(active_seconds, 0), COALESCE(valid_videos_count, 0)
            INTO v_f_sec, v_f_vids
            FROM public.vyora_earn_daily_activity
            WHERE user_id = v_f.user_id AND cairo_date = v_today;

            v_f_sec := COALESCE(v_f_sec, 0);
            v_f_vids := COALESCE(v_f_vids, 0);
            v_f_streak := public.vyora_earn_calc_friend_streak(v_f.user_id);
            v_f_done := v_f.is_device_eligible AND NOT v_f.suspicious_flag
                AND v_f_streak >= COALESCE(v_cfg.friend_days_required, 10);
            IF v_f_done THEN
                v_completed := v_completed + 1;
            END IF;

            v_friends := v_friends || jsonb_build_object(
                'userId', v_f.user_id,
                'username', v_f.username,
                'displayName', v_f.display_name,
                'avatarUrl', v_f.avatar_url,
                'registeredAt', v_f.created_at,
                'completedDays', v_f_streak,
                'requiredDays', COALESCE(v_cfg.friend_days_required, 10),
                'todayActiveMinutes', FLOOR(v_f_sec / 60.0)::BIGINT,
                'todayActiveSeconds', v_f_sec,
                'requiredDailyMinutes', COALESCE(v_cfg.friend_daily_minutes_required, 30),
                'todayValidVideos', v_f_vids,
                'requiredDailyVideos', COALESCE(v_cfg.friend_daily_videos_required, 2),
                'isCompleted', v_f_done,
                'isDeviceEligible', v_f.is_device_eligible,
                'suspiciousFlag', v_f.suspicious_flag,
                'suspiciousReason', v_f.suspicious_reason
            );
        END LOOP;

        v_participants := v_participants || jsonb_build_object(
            'userId', v_p.user_id,
            'username', v_p.username,
            'displayName', v_p.display_name,
            'email', v_p.email,
            'avatarUrl', v_p.avatar_url,
            'referralCode', v_p.referral_code,
            'referredByCode', v_p.referred_by_code,
            'personalVideosCount', v_vids,
            'invitedFriendsCount', v_invited,
            'completedFriendsCount', v_completed,
            'status', v_p.status,
            'reviewNote', v_p.review_note,
            'isDeviceEligible', v_p.is_device_eligible,
            'suspiciousFlag', v_p.suspicious_flag,
            'suspiciousReason', v_p.suspicious_reason,
            'deviceFingerprint', v_p.device_fingerprint,
            'playIntegrityStatus', v_p.play_integrity_status,
            'paidOutAt', v_p.paid_out_at,
            'friends', v_friends
        );
    END LOOP;

    SELECT COALESCE(jsonb_agg(jsonb_build_object(
        'deviceFingerprint', d.device_fingerprint,
        'appSetId', d.app_set_id,
        'playIntegrityStatus', d.play_integrity_status,
        'firstUserId', d.first_user_id,
        'linkedUserIds', d.linked_user_ids,
        'isFlagged', d.is_flagged,
        'flagReason', d.flag_reason,
        'adminDecision', d.admin_decision,
        'adminNote', d.admin_note,
        'updatedAt', d.updated_at
    ) ORDER BY d.updated_at DESC), '[]'::jsonb)
    INTO v_devices
    FROM public.vyora_device_bindings d
    WHERE d.is_flagged = TRUE OR array_length(d.linked_user_ids, 1) > 1 OR d.admin_decision <> 'clear';

    SELECT COALESCE(jsonb_agg(jsonb_build_object(
        'id', l.id,
        'userId', l.user_id,
        'username', COALESCE(pr.username, LEFT(l.user_id, 8)),
        'rewardEgp', l.reward_egp,
        'rewardPoints', l.reward_points,
        'decision', l.decision,
        'note', l.note,
        'reviewedBy', l.reviewed_by,
        'createdAt', l.created_at
    ) ORDER BY l.created_at DESC), '[]'::jsonb)
    INTO v_logs
    FROM public.vyora_earn_payout_logs l
    LEFT JOIN public.vyora_profiles pr ON pr.user_id = l.user_id;

    RETURN jsonb_build_object(
        'config', jsonb_build_object(
            'isEnabled', COALESCE(v_cfg.is_enabled, TRUE),
            'rewardEgp', COALESCE(v_cfg.reward_egp, 1500.00),
            'rewardPoints', COALESCE(v_cfg.reward_points, 150000),
            'inviterVideosRequired', COALESCE(v_cfg.inviter_videos_required, 10),
            'requiredFriends', COALESCE(v_cfg.required_friends, 10),
            'friendDaysRequired', COALESCE(v_cfg.friend_days_required, 10),
            'friendDailyMinutesRequired', COALESCE(v_cfg.friend_daily_minutes_required, 30),
            'friendDailyVideosRequired', COALESCE(v_cfg.friend_daily_videos_required, 2)
        ),
        'participants', v_participants,
        'flaggedDevices', v_devices,
        'payoutLogs', v_logs
    );
END;
$$;

-- ============================================================================
-- Automatic Repair: Convert Any Pre-Existing BIGINT / UUID ID Columns to TEXT
-- (Prevents "invalid input syntax for type bigint" if tables were created via UI)
-- ============================================================================

DO $$
DECLARE
    v_pol RECORD;
    v_col RECORD;
    v_is_identity TEXT;
    v_col_default TEXT;
BEGIN
    -- Drop existing RLS policies on vyora_* tables temporarily so ALTER COLUMN TYPE succeeds cleanly
    FOR v_pol IN
        SELECT schemaname, tablename, policyname
        FROM pg_policies
        WHERE schemaname = 'public' AND tablename LIKE 'vyora\_%' ESCAPE '\'
    LOOP
        EXECUTE format('DROP POLICY IF EXISTS %I ON %I.%I', v_pol.policyname, v_pol.schemaname, v_pol.tablename);
    END LOOP;

    FOR v_col IN
        SELECT table_name, column_name, data_type, is_identity, column_default
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name LIKE 'vyora\_%' ESCAPE '\'
          AND column_name IN (
              'id', 'user_id', 'host_id', 'stream_id', 'video_id', 'creator_uid',
              'sound_id', 'creator_id', 'follower_id', 'following_id', 'requester_id',
              'target_id', 'last_sender_id', 'conversation_id', 'sender_id',
              'recipient_id', 'actor_id', 'reporter_id', 'reviewed_by', 'assigned_by',
              'referred_by_uid', 'first_user_id', 'approved_by', 'updated_by'
          )
          AND data_type IN ('bigint', 'integer', 'smallint', 'uuid')
    LOOP
        IF v_col.is_identity = 'YES' THEN
            EXECUTE format('ALTER TABLE public.%I ALTER COLUMN %I DROP IDENTITY IF EXISTS', v_col.table_name, v_col.column_name);
        END IF;
        IF v_col.column_default IS NOT NULL AND v_col.column_default LIKE 'nextval%' THEN
            EXECUTE format('ALTER TABLE public.%I ALTER COLUMN %I DROP DEFAULT', v_col.table_name, v_col.column_name);
        END IF;
        EXECUTE format(
            'ALTER TABLE public.%I ALTER COLUMN %I TYPE TEXT USING %I::text',
            v_col.table_name, v_col.column_name, v_col.column_name
        );
    END LOOP;
END;
$$;

-- ============================================================================
-- Row-Level Security (RLS) Policies
-- ============================================================================

ALTER TABLE public.vyora_profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.vyora_follows ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.vyora_follow_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.vyora_blocks ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.vyora_videos_ledger ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.vyora_video_likes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.vyora_video_bookmarks ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.vyora_video_views ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.vyora_video_comments ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.vyora_sounds ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.vyora_hashtags ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.vyora_conversations ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.vyora_messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.vyora_notifications ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.vyora_live_streams ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.vyora_live_comments ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.vyora_reports ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.vyora_wallets ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.vyora_wallet_transactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.vyora_withdrawal_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.vyora_admin_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.vyora_admin_roles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.vyora_earn_config ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.vyora_device_bindings ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.vyora_earn_enrollments ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.vyora_earn_daily_activity ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.vyora_earn_payout_logs ENABLE ROW LEVEL SECURITY;

-- Profiles
DROP POLICY IF EXISTS "Profiles readable by authenticated" ON public.vyora_profiles;
CREATE POLICY "Profiles readable by authenticated" ON public.vyora_profiles FOR SELECT TO authenticated USING (TRUE);
DROP POLICY IF EXISTS "Own profile insertable" ON public.vyora_profiles;
CREATE POLICY "Own profile insertable" ON public.vyora_profiles FOR INSERT TO authenticated WITH CHECK (auth.uid()::text = user_id);
DROP POLICY IF EXISTS "Own or admin profile updatable" ON public.vyora_profiles;
CREATE POLICY "Own or admin profile updatable" ON public.vyora_profiles FOR UPDATE TO authenticated USING (auth.uid()::text = user_id OR public.vyora_is_admin()) WITH CHECK (auth.uid()::text = user_id OR public.vyora_is_admin());

-- Follows & Requests & Blocks
DROP POLICY IF EXISTS "Follows readable" ON public.vyora_follows;
CREATE POLICY "Follows readable" ON public.vyora_follows FOR SELECT TO authenticated USING (TRUE);
DROP POLICY IF EXISTS "Follow requests participant read" ON public.vyora_follow_requests;
CREATE POLICY "Follow requests participant read" ON public.vyora_follow_requests FOR SELECT TO authenticated USING (auth.uid()::text IN (requester_id, target_id));
DROP POLICY IF EXISTS "Blocks owner read" ON public.vyora_blocks;
CREATE POLICY "Blocks owner read" ON public.vyora_blocks FOR SELECT TO authenticated USING (auth.uid()::text = user_id);
DROP POLICY IF EXISTS "Blocks owner write" ON public.vyora_blocks;
CREATE POLICY "Blocks owner write" ON public.vyora_blocks FOR ALL TO authenticated USING (auth.uid()::text = user_id) WITH CHECK (auth.uid()::text = user_id);

-- Videos & Engagement
DROP POLICY IF EXISTS "Public active videos readable" ON public.vyora_videos_ledger;
CREATE POLICY "Public active videos readable" ON public.vyora_videos_ledger FOR SELECT TO anon, authenticated USING (status = 'active' OR auth.uid()::text = creator_uid OR public.vyora_is_admin());
DROP POLICY IF EXISTS "Creator insert own video" ON public.vyora_videos_ledger;
CREATE POLICY "Creator insert own video" ON public.vyora_videos_ledger FOR INSERT TO authenticated WITH CHECK (auth.uid()::text = creator_uid);
DROP POLICY IF EXISTS "Creator or admin video update" ON public.vyora_videos_ledger;
CREATE POLICY "Creator or admin video update" ON public.vyora_videos_ledger FOR UPDATE TO authenticated USING (auth.uid()::text = creator_uid OR public.vyora_is_admin());
DROP POLICY IF EXISTS "Creator or admin video delete" ON public.vyora_videos_ledger;
CREATE POLICY "Creator or admin video delete" ON public.vyora_videos_ledger FOR DELETE TO authenticated USING (auth.uid()::text = creator_uid OR public.vyora_is_admin());

DROP POLICY IF EXISTS "Likes readable" ON public.vyora_video_likes;
CREATE POLICY "Likes readable" ON public.vyora_video_likes FOR SELECT TO authenticated USING (TRUE);
DROP POLICY IF EXISTS "Own bookmarks readable" ON public.vyora_video_bookmarks;
CREATE POLICY "Own bookmarks readable" ON public.vyora_video_bookmarks FOR SELECT TO authenticated USING (auth.uid()::text = user_id);
DROP POLICY IF EXISTS "Comments readable" ON public.vyora_video_comments;
CREATE POLICY "Comments readable" ON public.vyora_video_comments FOR SELECT TO anon, authenticated USING (TRUE);
DROP POLICY IF EXISTS "Sounds readable" ON public.vyora_sounds;
CREATE POLICY "Sounds readable" ON public.vyora_sounds FOR SELECT TO anon, authenticated USING (is_licensed = TRUE OR auth.uid()::text = creator_id);
DROP POLICY IF EXISTS "Authenticated create sounds" ON public.vyora_sounds;
CREATE POLICY "Authenticated create sounds" ON public.vyora_sounds FOR INSERT TO authenticated WITH CHECK (auth.uid()::text = creator_id);
DROP POLICY IF EXISTS "Hashtags readable" ON public.vyora_hashtags;
CREATE POLICY "Hashtags readable" ON public.vyora_hashtags FOR SELECT TO anon, authenticated USING (TRUE);

-- Chat & Notifications
DROP POLICY IF EXISTS "Conversations participant read" ON public.vyora_conversations;
CREATE POLICY "Conversations participant read" ON public.vyora_conversations FOR ALL TO authenticated USING (auth.uid()::text = ANY(participant_ids)) WITH CHECK (auth.uid()::text = ANY(participant_ids));
DROP POLICY IF EXISTS "Messages participant read" ON public.vyora_messages;
CREATE POLICY "Messages participant read" ON public.vyora_messages FOR ALL TO authenticated USING (auth.uid()::text = ANY(participant_ids)) WITH CHECK (auth.uid()::text = ANY(participant_ids));
DROP POLICY IF EXISTS "Own notifications read update" ON public.vyora_notifications;
CREATE POLICY "Own notifications read update" ON public.vyora_notifications FOR ALL TO authenticated USING (auth.uid()::text = recipient_id);

-- Live Streams & Comments
DROP POLICY IF EXISTS "Live streams readable" ON public.vyora_live_streams;
CREATE POLICY "Live streams readable" ON public.vyora_live_streams FOR SELECT TO authenticated USING (TRUE);
DROP POLICY IF EXISTS "Live streams insert" ON public.vyora_live_streams;
CREATE POLICY "Live streams insert" ON public.vyora_live_streams FOR INSERT TO authenticated WITH CHECK (auth.uid()::text = host_id);
DROP POLICY IF EXISTS "Live streams host or admin update" ON public.vyora_live_streams;
CREATE POLICY "Live streams host or admin update" ON public.vyora_live_streams FOR UPDATE TO authenticated USING (auth.uid()::text = host_id OR public.vyora_is_admin());

DROP POLICY IF EXISTS "Live comments readable" ON public.vyora_live_comments;
CREATE POLICY "Live comments readable" ON public.vyora_live_comments FOR SELECT TO authenticated USING (TRUE);
DROP POLICY IF EXISTS "Live comments insert" ON public.vyora_live_comments;
CREATE POLICY "Live comments insert" ON public.vyora_live_comments FOR INSERT TO authenticated WITH CHECK (auth.uid()::text = user_id);

-- Reports & Admin & Wallets
DROP POLICY IF EXISTS "Reports create auth read admin" ON public.vyora_reports;
DROP POLICY IF EXISTS "Reports create auth" ON public.vyora_reports;
CREATE POLICY "Reports create auth" ON public.vyora_reports FOR INSERT TO authenticated WITH CHECK (auth.uid()::text = reporter_id);
DROP POLICY IF EXISTS "Reports admin read update" ON public.vyora_reports;
CREATE POLICY "Reports admin read update" ON public.vyora_reports FOR ALL TO authenticated USING (public.vyora_is_admin());

DROP POLICY IF EXISTS "Own wallet readable" ON public.vyora_wallets;
CREATE POLICY "Own wallet readable" ON public.vyora_wallets FOR SELECT TO authenticated USING (auth.uid()::text = user_id OR public.vyora_is_admin());
DROP POLICY IF EXISTS "Own transactions readable" ON public.vyora_wallet_transactions;
CREATE POLICY "Own transactions readable" ON public.vyora_wallet_transactions FOR SELECT TO authenticated USING (auth.uid()::text = user_id OR public.vyora_is_admin());
DROP POLICY IF EXISTS "Own or admin withdrawals readable" ON public.vyora_withdrawal_requests;
CREATE POLICY "Own or admin withdrawals readable" ON public.vyora_withdrawal_requests FOR SELECT TO authenticated USING (auth.uid()::text = user_id OR public.vyora_is_admin());

DROP POLICY IF EXISTS "Settings readable by authenticated" ON public.vyora_admin_settings;
CREATE POLICY "Settings readable by authenticated" ON public.vyora_admin_settings FOR SELECT TO anon, authenticated USING (TRUE);
DROP POLICY IF EXISTS "Settings writable by admin" ON public.vyora_admin_settings;
CREATE POLICY "Settings writable by admin" ON public.vyora_admin_settings FOR ALL TO authenticated USING (public.vyora_is_admin()) WITH CHECK (public.vyora_is_admin());

DROP POLICY IF EXISTS "Admin roles readable" ON public.vyora_admin_roles;
CREATE POLICY "Admin roles readable" ON public.vyora_admin_roles FOR SELECT TO authenticated USING (auth.uid()::text = user_id OR public.vyora_is_admin());
DROP POLICY IF EXISTS "Admin roles writable by admin" ON public.vyora_admin_roles;
CREATE POLICY "Admin roles writable by admin" ON public.vyora_admin_roles FOR ALL TO authenticated USING (public.vyora_is_admin()) WITH CHECK (public.vyora_is_admin());

-- Earn With Us Program RLS
DROP POLICY IF EXISTS "Earn config readable" ON public.vyora_earn_config;
CREATE POLICY "Earn config readable" ON public.vyora_earn_config FOR SELECT TO anon, authenticated USING (TRUE);
DROP POLICY IF EXISTS "Earn config admin writable" ON public.vyora_earn_config;
CREATE POLICY "Earn config admin writable" ON public.vyora_earn_config FOR ALL TO authenticated USING (public.vyora_is_admin()) WITH CHECK (public.vyora_is_admin());

DROP POLICY IF EXISTS "Device bindings admin readable" ON public.vyora_device_bindings;
CREATE POLICY "Device bindings admin readable" ON public.vyora_device_bindings FOR SELECT TO authenticated USING (public.vyora_is_admin() OR auth.uid()::text = ANY(linked_user_ids));
DROP POLICY IF EXISTS "Device bindings admin writable" ON public.vyora_device_bindings;
CREATE POLICY "Device bindings admin writable" ON public.vyora_device_bindings FOR ALL TO authenticated USING (public.vyora_is_admin()) WITH CHECK (public.vyora_is_admin());

DROP POLICY IF EXISTS "Own or invited earn enrollment readable" ON public.vyora_earn_enrollments;
CREATE POLICY "Own or invited earn enrollment readable" ON public.vyora_earn_enrollments FOR SELECT TO authenticated USING (auth.uid()::text IN (user_id, referred_by_uid) OR public.vyora_is_admin());
DROP POLICY IF EXISTS "Earn enrollments admin writable" ON public.vyora_earn_enrollments;
CREATE POLICY "Earn enrollments admin writable" ON public.vyora_earn_enrollments FOR ALL TO authenticated USING (public.vyora_is_admin()) WITH CHECK (public.vyora_is_admin());

DROP POLICY IF EXISTS "Own or admin daily activity readable" ON public.vyora_earn_daily_activity;
CREATE POLICY "Own or admin daily activity readable" ON public.vyora_earn_daily_activity FOR SELECT TO authenticated USING (auth.uid()::text = user_id OR public.vyora_is_admin());

DROP POLICY IF EXISTS "Own or admin earn payout logs readable" ON public.vyora_earn_payout_logs;
CREATE POLICY "Own or admin earn payout logs readable" ON public.vyora_earn_payout_logs FOR SELECT TO authenticated USING (auth.uid()::text = user_id OR public.vyora_is_admin());

DROP POLICY IF EXISTS "Own watch wheel cycles readable" ON public.vyora_watch_wheel_cycles;
CREATE POLICY "Own watch wheel cycles readable" ON public.vyora_watch_wheel_cycles FOR SELECT TO authenticated USING (auth.uid()::text = user_id OR public.vyora_is_admin());

-- ============================================================================
-- Watch Earnings Wheel RPCs
-- ============================================================================

CREATE OR REPLACE FUNCTION public.vyora_get_watch_wheel_status()
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_uid TEXT := auth.uid()::text;
    v_today DATE := (NOW() AT TIME ZONE 'Africa/Cairo')::date;
    v_settings public.vyora_admin_settings%ROWTYPE;
    v_today_cycles BIGINT := 0;
BEGIN
    SELECT * INTO v_settings FROM public.vyora_admin_settings WHERE id = 'global';
    IF v_uid IS NOT NULL AND v_uid <> '' THEN
        SELECT COALESCE(COUNT(*), 0) INTO v_today_cycles
        FROM public.vyora_watch_wheel_cycles
        WHERE user_id = v_uid AND cairo_date = v_today;
    END IF;
    RETURN jsonb_build_object(
        'enabled', COALESCE(v_settings.watch_wheel_enabled, TRUE),
        'cycleSec', GREATEST(10, COALESCE(v_settings.watch_wheel_cycle_sec, 30)),
        'pointsPerCycle', GREATEST(1, COALESCE(v_settings.watch_wheel_points_per_cycle, 5)),
        'dailyCapCycles', GREATEST(1, COALESCE(v_settings.watch_wheel_daily_cap_cycles, 60)),
        'todayCycles', v_today_cycles
    );
END;
$$;

CREATE OR REPLACE FUNCTION public.vyora_claim_watch_wheel_reward(
    p_video_id TEXT DEFAULT '',
    p_watched_sec BIGINT DEFAULT 30
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_uid TEXT := auth.uid()::text;
    v_today DATE := (NOW() AT TIME ZONE 'Africa/Cairo')::date;
    v_settings public.vyora_admin_settings%ROWTYPE;
    v_today_cycles BIGINT := 0;
    v_next_cycle BIGINT := 1;
    v_cycle_sec BIGINT;
    v_points_per_cycle BIGINT;
    v_daily_cap BIGINT;
    v_last_claim TIMESTAMPTZ;
    v_amount_delta NUMERIC(12, 4);
    v_ref TEXT;
    v_wallet_points BIGINT := 0;
BEGIN
    IF v_uid IS NULL OR v_uid = '' THEN
        RAISE EXCEPTION 'Authentication required';
    END IF;

    INSERT INTO public.vyora_admin_settings (id) VALUES ('global') ON CONFLICT (id) DO NOTHING;
    SELECT * INTO v_settings FROM public.vyora_admin_settings WHERE id = 'global';

    v_cycle_sec := GREATEST(10, COALESCE(v_settings.watch_wheel_cycle_sec, 30));
    v_points_per_cycle := GREATEST(1, COALESCE(v_settings.watch_wheel_points_per_cycle, 5));
    v_daily_cap := GREATEST(1, COALESCE(v_settings.watch_wheel_daily_cap_cycles, 60));

    SELECT COALESCE(COUNT(*), 0), MAX(created_at)
    INTO v_today_cycles, v_last_claim
    FROM public.vyora_watch_wheel_cycles
    WHERE user_id = v_uid AND cairo_date = v_today;

    INSERT INTO public.vyora_wallets (user_id, points, balance_usd, total_earned_usd)
    VALUES (v_uid, 0, 0, 0)
    ON CONFLICT (user_id) DO NOTHING;

    SELECT points INTO v_wallet_points FROM public.vyora_wallets WHERE user_id = v_uid;

    IF NOT COALESCE(v_settings.watch_wheel_enabled, TRUE) THEN
        RETURN jsonb_build_object(
            'awarded', FALSE,
            'reason', 'disabled',
            'pointsDelta', 0,
            'todayCycles', v_today_cycles,
            'dailyCapCycles', v_daily_cap,
            'walletPoints', v_wallet_points
        );
    END IF;

    IF v_today_cycles >= v_daily_cap THEN
        RETURN jsonb_build_object(
            'awarded', FALSE,
            'reason', 'daily_cap_reached',
            'pointsDelta', 0,
            'todayCycles', v_today_cycles,
            'dailyCapCycles', v_daily_cap,
            'walletPoints', v_wallet_points
        );
    END IF;

    IF COALESCE(p_watched_sec, 0) < GREATEST(8, v_cycle_sec - 2) THEN
        RETURN jsonb_build_object(
            'awarded', FALSE,
            'reason', 'insufficient_watch_time',
            'pointsDelta', 0,
            'todayCycles', v_today_cycles,
            'dailyCapCycles', v_daily_cap,
            'walletPoints', v_wallet_points
        );
    END IF;

    IF v_last_claim IS NOT NULL AND EXTRACT(EPOCH FROM (NOW() - v_last_claim)) < GREATEST(8, v_cycle_sec - 4) THEN
        RETURN jsonb_build_object(
            'awarded', FALSE,
            'reason', 'cooldown',
            'pointsDelta', 0,
            'todayCycles', v_today_cycles,
            'dailyCapCycles', v_daily_cap,
            'walletPoints', v_wallet_points
        );
    END IF;

    v_next_cycle := v_today_cycles + 1;
    v_ref := 'wheel:' || v_uid || ':' || v_today::text || ':' || v_next_cycle::text;
    v_amount_delta := ROUND((v_points_per_cycle * COALESCE(v_settings.point_value_usd, 0.01))::numeric, 4);

    INSERT INTO public.vyora_watch_wheel_cycles (
        id, user_id, cairo_date, cycle_index, video_id, points_awarded
    )
    VALUES (
        v_ref, v_uid, v_today, v_next_cycle, COALESCE(p_video_id, ''), v_points_per_cycle
    )
    ON CONFLICT (user_id, cairo_date, cycle_index) DO NOTHING;

    UPDATE public.vyora_wallets
    SET points = points + v_points_per_cycle,
        balance_usd = balance_usd + v_amount_delta,
        total_earned_usd = total_earned_usd + v_amount_delta,
        updated_at = NOW()
    WHERE user_id = v_uid
    RETURNING points INTO v_wallet_points;

    INSERT INTO public.vyora_wallet_transactions (
        external_ref, user_id, tx_type, points_delta, amount_delta, description, status
    )
    VALUES (
        v_ref,
        v_uid,
        'watch_wheel',
        v_points_per_cycle,
        v_amount_delta,
        'Watch wheel reward cycle #' || v_next_cycle::text,
        'completed'
    )
    ON CONFLICT (external_ref) DO NOTHING;

    RETURN jsonb_build_object(
        'awarded', TRUE,
        'reason', 'credited',
        'pointsDelta', v_points_per_cycle,
        'amountDelta', v_amount_delta,
        'todayCycles', v_next_cycle,
        'dailyCapCycles', v_daily_cap,
        'walletPoints', v_wallet_points
    );
END;
$$;

CREATE OR REPLACE FUNCTION public.vyora_admin_update_watch_wheel(
    p_enabled BOOLEAN,
    p_cycle_sec BIGINT DEFAULT 30,
    p_points_per_cycle BIGINT DEFAULT 5,
    p_daily_cap_cycles BIGINT DEFAULT 60
)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_uid TEXT := auth.uid()::text;
BEGIN
    IF NOT public.vyora_is_admin() THEN
        RAISE EXCEPTION 'Admin privileges required';
    END IF;

    INSERT INTO public.vyora_admin_settings (
        id, watch_wheel_enabled, watch_wheel_cycle_sec,
        watch_wheel_points_per_cycle, watch_wheel_daily_cap_cycles,
        updated_by, updated_at
    )
    VALUES (
        'global',
        COALESCE(p_enabled, TRUE),
        LEAST(180, GREATEST(10, COALESCE(p_cycle_sec, 30))),
        LEAST(500, GREATEST(1, COALESCE(p_points_per_cycle, 5))),
        LEAST(500, GREATEST(1, COALESCE(p_daily_cap_cycles, 60))),
        v_uid,
        NOW()
    )
    ON CONFLICT (id) DO UPDATE
    SET watch_wheel_enabled = EXCLUDED.watch_wheel_enabled,
        watch_wheel_cycle_sec = EXCLUDED.watch_wheel_cycle_sec,
        watch_wheel_points_per_cycle = EXCLUDED.watch_wheel_points_per_cycle,
        watch_wheel_daily_cap_cycles = EXCLUDED.watch_wheel_daily_cap_cycles,
        updated_by = v_uid,
        updated_at = NOW();
END;
$$;

-- ============================================================================
-- Supabase Storage Bucket & Policies (vyora-media)
-- ============================================================================

INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES ('vyora-media', 'vyora-media', TRUE, NULL, NULL)
ON CONFLICT (id) DO UPDATE
SET name = EXCLUDED.name,
    public = TRUE,
    file_size_limit = NULL,
    allowed_mime_types = NULL;

DROP POLICY IF EXISTS "Public read vyora-media" ON storage.objects;
CREATE POLICY "Public read vyora-media"
    ON storage.objects FOR SELECT
    TO anon, authenticated
    USING (bucket_id = 'vyora-media');

DROP POLICY IF EXISTS "Authenticated upload vyora-media" ON storage.objects;
CREATE POLICY "Authenticated upload vyora-media"
    ON storage.objects FOR INSERT
    TO authenticated
    WITH CHECK (
        bucket_id = 'vyora-media'
        AND ((storage.foldername(name))[2] = auth.uid()::text OR owner = auth.uid())
    );

DROP POLICY IF EXISTS "Authenticated update own vyora-media" ON storage.objects;
CREATE POLICY "Authenticated update own vyora-media"
    ON storage.objects FOR UPDATE
    TO authenticated
    USING (
        bucket_id = 'vyora-media'
        AND ((storage.foldername(name))[2] = auth.uid()::text OR owner = auth.uid())
    )
    WITH CHECK (
        bucket_id = 'vyora-media'
        AND ((storage.foldername(name))[2] = auth.uid()::text OR owner = auth.uid())
    );

DROP POLICY IF EXISTS "Authenticated delete own vyora-media" ON storage.objects;
CREATE POLICY "Authenticated delete own vyora-media"
    ON storage.objects FOR DELETE
    TO authenticated
    USING (
        bucket_id = 'vyora-media'
        AND ((storage.foldername(name))[2] = auth.uid()::text OR owner = auth.uid())
    );

-- ============================================================================
-- Schema Permissions & PostgREST Cache Reload
-- ============================================================================

GRANT USAGE ON SCHEMA public TO anon, authenticated, service_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO authenticated, service_role;
GRANT SELECT ON public.vyora_videos_ledger, public.vyora_video_comments, public.vyora_sounds, public.vyora_hashtags, public.vyora_admin_settings, public.vyora_earn_config TO anon;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO anon, authenticated, service_role;

NOTIFY pgrst, 'reload schema';
