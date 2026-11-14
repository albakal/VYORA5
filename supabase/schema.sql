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

CREATE INDEX IF NOT EXISTS idx_vyora_videos_creator ON public.vyora_videos_ledger(creator_uid);
CREATE INDEX IF NOT EXISTS idx_vyora_videos_status_created ON public.vyora_videos_ledger(status, created_at DESC);

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
    tx_type TEXT NOT NULL CHECK (tx_type IN ('view_reward', 'creator_bonus', 'withdrawal', 'adjustment')),
    points_delta BIGINT NOT NULL,
    amount_delta NUMERIC(12, 4) NOT NULL,
    description TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'completed' CHECK (status IN ('completed', 'pending', 'rejected')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
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
    updated_by TEXT NOT NULL DEFAULT '',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO public.vyora_admin_settings (id) VALUES ('global') ON CONFLICT (id) DO NOTHING;

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
    v_base_name TEXT;
    v_handle TEXT;
BEGIN
    IF v_uid IS NULL OR v_uid = '' THEN
        RAISE EXCEPTION 'Authentication required';
    END IF;

    v_base_name := COALESCE(NULLIF(TRIM(p_display_name), ''), NULLIF(SPLIT_PART(p_email, '@', 1), ''), 'vyora_creator');
    v_handle := LOWER(REGEXP_REPLACE(v_base_name, '[^a-zA-Z0-9_]', '_', 'g'));
    v_handle := TRIM(BOTH '_' FROM v_handle);
    IF v_handle = '' THEN
        v_handle := 'creator_' || LEFT(v_uid, 6);
    END IF;
    v_handle := LEFT(v_handle, 30);

    INSERT INTO public.vyora_profiles (user_id, email, username, display_name, avatar_url)
    VALUES (v_uid, COALESCE(p_email, ''), v_handle, LEFT(v_base_name, 60), COALESCE(p_avatar_url, ''))
    ON CONFLICT (user_id) DO UPDATE
    SET email = CASE WHEN vyora_profiles.email = '' THEN EXCLUDED.email ELSE vyora_profiles.email END,
        updated_at = NOW();

    INSERT INTO public.vyora_wallets (user_id, points, balance_usd, total_earned_usd)
    VALUES (v_uid, 0, 0, 0)
    ON CONFLICT (user_id) DO NOTHING;

    RETURN QUERY SELECT * FROM public.vyora_profiles WHERE user_id = v_uid;
END;
$$;

CREATE OR REPLACE FUNCTION public.vyora_publish_video(
    p_video_url TEXT,
    p_thumbnail_url TEXT,
    p_caption TEXT,
    p_hashtags TEXT[],
    p_sound_id TEXT,
    p_sound_title TEXT,
    p_duration_sec BIGINT
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
BEGIN
    IF v_uid IS NULL OR v_uid = '' THEN
        RAISE EXCEPTION 'Authentication required';
    END IF;

    SELECT * INTO v_profile FROM public.vyora_profiles WHERE user_id = v_uid;

    INSERT INTO public.vyora_videos_ledger (
        video_id, creator_uid, creator_username, creator_avatar_url,
        video_url, thumbnail_url, caption, hashtags, sound_id, sound_title, duration_sec, status
    )
    VALUES (
        v_video_id,
        v_uid,
        COALESCE(v_profile.username, 'creator_' || LEFT(v_uid, 6)),
        COALESCE(v_profile.avatar_url, ''),
        p_video_url,
        COALESCE(p_thumbnail_url, ''),
        LEFT(COALESCE(p_caption, ''), 500),
        COALESCE(p_hashtags, '{}'),
        COALESCE(p_sound_id, ''),
        LEFT(COALESCE(NULLIF(p_sound_title, ''), 'Original Sound'), 120),
        LEAST(300, GREATEST(1, COALESCE(p_duration_sec, 15))),
        'active'
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
    v_clean_url TEXT := LEFT(TRIM(COALESCE(p_avatar_url, '')), 1500);
BEGIN
    IF v_uid IS NULL OR v_uid = '' THEN
        RAISE EXCEPTION 'Authentication required';
    END IF;

    UPDATE public.vyora_profiles
    SET avatar_url = v_clean_url,
        updated_at = NOW()
    WHERE user_id = v_uid;

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
              'recipient_id', 'actor_id', 'reporter_id', 'reviewed_by', 'assigned_by'
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

-- ============================================================================
-- Supabase Storage Bucket & Policies (vyora-media)
-- ============================================================================

INSERT INTO storage.buckets (id, name, public)
VALUES ('vyora-media', 'vyora-media', TRUE)
ON CONFLICT (id) DO UPDATE SET public = TRUE;

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
GRANT SELECT ON public.vyora_videos_ledger, public.vyora_video_comments, public.vyora_sounds, public.vyora_hashtags, public.vyora_admin_settings TO anon;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO anon, authenticated, service_role;

NOTIFY pgrst, 'reload schema';
