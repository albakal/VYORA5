// Supabase Edge Function: get-agora-token
// Deploy with: supabase functions deploy get-agora-token
// Set secrets: supabase secrets set AGORA_APP_ID=... AGORA_APP_CERTIFICATE=...

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

function crc32(buf: Uint8Array): number {
  let crc = 0xffffffff;
  for (let i = 0; i < buf.length; i++) {
    crc ^= buf[i];
    for (let j = 0; j < 8; j++) {
      crc = (crc >>> 1) ^ (0xedb88320 & -(crc & 1));
    }
  }
  return (crc ^ 0xffffffff) >>> 0;
}

class ByteBuf {
  private bytes: number[] = [];

  putUint16(v: number) {
    this.bytes.push(v & 0xff, (v >>> 8) & 0xff);
    return this;
  }

  putUint32(v: number) {
    this.bytes.push(v & 0xff, (v >>> 8) & 0xff, (v >>> 16) & 0xff, (v >>> 24) & 0xff);
    return this;
  }

  putBytes(arr: Uint8Array) {
    this.putUint16(arr.length);
    for (const b of arr) this.bytes.push(b);
    return this;
  }

  putString(s: string) {
    return this.putBytes(new TextEncoder().encode(s));
  }

  putTreeMapUInt32(map: Record<number, number>) {
    const keys = Object.keys(map).map(Number).sort((a, b) => a - b);
    this.putUint16(keys.length);
    for (const k of keys) {
      this.putUint16(k);
      this.putUint32(map[k]);
    }
    return this;
  }

  asUint8Array(): Uint8Array {
    return new Uint8Array(this.bytes);
  }
}

async function hmacSha256(key: string, data: Uint8Array): Promise<Uint8Array> {
  const cryptoKey = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(key),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const sig = await crypto.subtle.sign("HMAC", cryptoKey, data);
  return new Uint8Array(sig);
}

async function buildRtcToken006(
  appId: string,
  appCertificate: string,
  channelName: string,
  uidStr: string,
  role: number,
  privilegeExpiredTs: number,
): Promise<string> {
  const ts = Math.floor(Date.now() / 1000);
  const salt = Math.floor(Math.random() * 100000000);
  const expiredTs = ts + 24 * 3600;

  const privileges: Record<number, number> = { 1: privilegeExpiredTs };
  if (role === 1) {
    privileges[2] = privilegeExpiredTs;
    privileges[3] = privilegeExpiredTs;
    privileges[4] = privilegeExpiredTs;
  }

  const msg = new ByteBuf().putUint32(salt).putUint32(expiredTs).putTreeMapUInt32(privileges).asUint8Array();

  const appIdBytes = new TextEncoder().encode(appId);
  const channelBytes = new TextEncoder().encode(channelName);
  const uidBytes = new TextEncoder().encode(uidStr);
  const toSign = new Uint8Array(appIdBytes.length + channelBytes.length + uidBytes.length + msg.length);
  let off = 0;
  toSign.set(appIdBytes, off); off += appIdBytes.length;
  toSign.set(channelBytes, off); off += channelBytes.length;
  toSign.set(uidBytes, off); off += uidBytes.length;
  toSign.set(msg, off);

  const signature = await hmacSha256(appCertificate, toSign);
  const content = new ByteBuf()
    .putBytes(signature)
    .putUint32(crc32(channelBytes))
    .putUint32(crc32(uidBytes))
    .putBytes(msg)
    .asUint8Array();

  const b64 = btoa(String.fromCharCode(...content));
  return `006${appId}${b64}`;
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    const authHeader = req.headers.get("Authorization") ?? "";
    const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
    const supabaseAnonKey = Deno.env.get("SUPABASE_ANON_KEY") ?? "";
    const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";

    const userClient = createClient(supabaseUrl, supabaseAnonKey, {
      global: { headers: { Authorization: authHeader } },
    });
    const { data: { user }, error: userErr } = await userClient.auth.getUser();
    if (userErr || !user) {
      return new Response(JSON.stringify({ error: "Unauthorized" }), {
        status: 401,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const { streamId } = await req.json();
    if (!streamId) {
      return new Response(JSON.stringify({ error: "streamId is required" }), {
        status: 400,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const appId = (Deno.env.get("AGORA_APP_ID") ?? "").trim();
    const appCertificate = (Deno.env.get("AGORA_APP_CERTIFICATE") ?? "").trim();
    if (!appId || !appCertificate) {
      return new Response(
        JSON.stringify({ error: "Configure AGORA_APP_ID and AGORA_APP_CERTIFICATE in Supabase Edge Function Secrets." }),
        { status: 412, headers: { ...corsHeaders, "Content-Type": "application/json" } },
      );
    }

    const adminClient = createClient(supabaseUrl, serviceRoleKey);
    const { data: stream, error: streamErr } = await adminClient
      .from("vyora_live_streams")
      .select("channel_name, host_id, is_active")
      .eq("id", streamId)
      .single();

    if (streamErr || !stream || !stream.is_active) {
      return new Response(JSON.stringify({ error: "Live stream unavailable" }), {
        status: 404,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const isHost = stream.host_id === user.id;
    const expireAt = Math.floor(Date.now() / 1000) + 3600;
    const token = await buildRtcToken006(
      appId,
      appCertificate,
      stream.channel_name,
      "0",
      isHost ? 1 : 2,
      expireAt,
    );

    return new Response(
      JSON.stringify({
        appId,
        token,
        channelName: stream.channel_name,
        rtcUid: 0,
        role: isHost ? "publisher" : "subscriber",
      }),
      { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } },
    );
  } catch (err) {
    return new Response(JSON.stringify({ error: String(err) }), {
      status: 500,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }
});
