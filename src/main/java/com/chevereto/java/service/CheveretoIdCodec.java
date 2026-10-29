package com.chevereto.java.service;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Comparator;

/**
 * 与 Chevereto 原版完全一致的 ID 编解码（app/src/Legacy/functions.php::cheveretoID）。
 *
 * 规则：index = a-z0-9A-Z 共 62 字符；用 sha256(crypt_salt) 的前 62 位对 index 做降序洗牌；
 * 编码 = (id * id_padding) 转新索引下的 base62；解码为逆过程。
 */
@Service
public class CheveretoIdCodec {

    private static final String BASE_INDEX = "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    /** 原版新装默认 id_padding = 5000 */
    public static final long DEFAULT_ID_PADDING = 5000;

    private volatile String cachedSalt;
    private volatile char[] cachedShuffled;

    private final SettingsService settingsService;

    public CheveretoIdCodec(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    public String encode(long id) {
        long padding = padding();
        long v = id * padding;
        char[] index = shuffledIndex();
        int base = index.length;
        StringBuilder out = new StringBuilder();
        long in = v;
        for (int t = (int) (Math.log(in) / Math.log(base)); t >= 0; --t) {
            long bcp = (long) Math.pow(base, t);
            long a = (in / bcp) % base;
            out.append(index[(int) a]);
            in -= a * bcp;
        }
        return out.toString();
    }

    public long decode(String encoded) {
        char[] index = shuffledIndex();
        int base = index.length;
        long out = 0;
        int len = encoded.length() - 1;
        for (int t = 0; t <= len; ++t) {
            int pos = indexOf(index, encoded.charAt(t));
            if (pos < 0) {
                return -1;
            }
            out += (long) pos * (long) Math.pow(base, len - t);
        }
        long padding = padding();
        return padding > 0 ? out / padding : out;
    }

    private long padding() {
        long padding = settingsService.getLong("id_padding", (int) DEFAULT_ID_PADDING);
        return Math.max(0, padding);
    }

    private char[] shuffledIndex() {
        String salt = settingsService.get("crypt_salt");
        char[] cached = cachedShuffled;
        if (cached != null && salt.equals(cachedSalt)) {
            return cached;
        }
        synchronized (this) {
            if (cachedShuffled != null && salt.equals(cachedSalt)) {
                return cachedShuffled;
            }
            // passhash：sha256 hex（不足 62 位则 sha512，与原版一致；sha256 有 64 位足够）
            String passhash = sha256Hex(salt);
            // 原版 array_multisort($p, SORT_DESC, $i)：按 hash 字符降序同步重排 index
            AbstractMap.SimpleEntry<Character, Character>[] pairs = new AbstractMap.SimpleEntry[BASE_INDEX.length()];
            for (int n = 0; n < BASE_INDEX.length(); n++) {
                pairs[n] = new AbstractMap.SimpleEntry<>(passhash.charAt(n), BASE_INDEX.charAt(n));
            }
            Arrays.sort(pairs, Comparator.comparing((AbstractMap.SimpleEntry<Character, Character> e) -> e.getKey()).reversed());
            char[] shuffled = new char[pairs.length];
            for (int n = 0; n < pairs.length; n++) {
                shuffled[n] = pairs[n].getValue();
            }
            cachedShuffled = shuffled;
            cachedSalt = salt;
            return shuffled;
        }
    }

    private int indexOf(char[] arr, char c) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] == c) return i;
        }
        return -1;
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
