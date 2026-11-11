/*
 * Pruebas de la lógica crítica (fechas ISO con offset, vaivenes, fusión del historial
 * compartido, búsqueda de bancos y monto del conversor). Corre contra el CÓDIGO REAL.
 *
 * Cómo ejecutarlas (Termux):
 *   cd ~/re/supercambio
 *   curl -sL -o /tmp/json.jar https://repo1.maven.org/maven2/org/json/json/20231013/json-20231013.jar
 *   mkdir -p /tmp/tc && javac -nowarn -encoding UTF-8 -cp "/tmp/json.jar:android-jar/android.jar:libs/zxing-core-3.3.3.jar" \
 *       -d /tmp/tc app/src/com/supercambio/app/*.java app/gen/com/supercambio/app/R.java test/AuditTest.java
 *   java -cp "/tmp/tc:/tmp/json.jar:android-jar/android.jar" com.supercambio.app.AuditTest
 */
package com.supercambio.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Method;

/**
 * Prueba de la lógica tocada por la auditoría de v3.0.14.
 * Se ejecuta sobre el runtime de Android (dalvikvm) para usar el org.json real.
 */
public class AuditTest {

    static int fails = 0;

    static void check(boolean ok, String msg) {
        System.out.println((ok ? "OK   " : "FAIL ") + msg);
        if (!ok) fails++;
    }

    static JSONArray pts(long[] ts, double[] vs) {
        JSONArray a = new JSONArray();
        for (int i = 0; i < ts.length; i++) {
            JSONObject o = new JSONObject();
            try {
                o.put("t", ts[i]);
                o.put("v", vs[i]);
            } catch (Exception ignored) { }
            a.put(o);
        }
        return a;
    }

    static String values(JSONArray a) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < a.length(); i++) {
            try {
                if (i > 0) sb.append(",");
                sb.append(a.getJSONObject(i).getDouble("v"));
            } catch (Exception e) {
                sb.append("?");
            }
        }
        return sb.toString();
    }

    static long utc(String iso) {
        // "2026-11-11T04:00:00Z" -> epoch, para comparar sinZone horaria
        return java.time.Instant.parse(iso).toEpochMilli();
    }

    static Object newInstance(Class<?> c) throws Exception {
        // instancia sin ejecutar el constructor de Activity (el stub de android.jar no sirve)
        java.lang.reflect.Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        sun.misc.Unsafe u = (sun.misc.Unsafe) f.get(null);
        return u.allocateInstance(c);
    }

    public static void main(String[] args) throws Exception {
        Object act = newInstance(MainActivity.class);
        // ---------- 1. Fechas ISO con offset ----------
        check(MainActivity.tsOfISO("2026-11-11T00:00:00-04:00") == utc("2026-11-11T04:00:00Z"),
                "oficial con offset -04:00 -> " + MainActivity.tsOfISO("2026-11-11T00:00:00-04:00"));
        check(MainActivity.tsOfISO("2026-11-11T18:01:34.369Z") == utc("2026-11-11T18:01:34Z"),
                "paralelo en UTC (Z) -> " + MainActivity.tsOfISO("2026-11-11T18:01:34.369Z"));
        check(MainActivity.tsOfISO("2026-11-11T00:00:00") == utc("2026-11-11T04:00:00Z"),
                "sin offset -> se asume Caracas -> " + MainActivity.tsOfISO("2026-11-11T00:00:00"));
        check(MainActivity.tsOfISO("2026-11-11T12:00:00+02:00") == utc("2026-11-11T10:00:00Z"),
                "offset +02:00 -> " + MainActivity.tsOfISO("2026-11-11T12:00:00+02:00"));
        check(MainActivity.tsOfISO("2026") == 0, "fecha corta -> 0");
        check(MainActivity.tsOfISO("2026-13-45T99:00:00") == 0, "fecha imposible -> 0");
        check(MainActivity.tsOfISO("") == 0, "vacía -> 0");
        check(MainActivity.tsOfISO(null) == 0, "null -> 0");

        // ---------- 2. dropFlaps (vaivenes) ----------
        Method drop = MainActivity.class.getDeclaredMethod("dropFlaps", JSONArray.class, long.class);
        drop.setAccessible(true);
        long min = 1800000L;

        // A -> B -> S(spike) -> A : el cambio real B se conserva
        JSONArray r1 = (JSONArray) drop.invoke(act, pts(
                new long[]{0L + 1700000000000L, 1700000000000L + 600000L, 1700000000000L + 900000L, 1700000000000L + 1200000L},
                new double[]{36.50, 36.70, 99.99, 36.50}), min);
        check("36.5,36.7,99.99,36.5".equals(values(r1)), "dropFlaps conserva el cambio real: " + values(r1));

        // A -> B -> A en menos de 30 min : B es transitorio y se descarta
        JSONArray r2 = (JSONArray) drop.invoke(act, pts(
                new long[]{1700000000000L, 1700000000000L + 600000L, 1700000000000L + 900000L},
                new double[]{36.50, 36.70, 36.50}), min);
        check("36.5".equals(values(r2)), "dropFlaps descarta el vaivén: " + values(r2));

        // A -> B -> A después de 30 min : B es un cambio real
        JSONArray r3 = (JSONArray) drop.invoke(act, pts(
                new long[]{1700000000000L, 1700000000000L + 600000L, 1700000000000L + 3600000L},
                new double[]{36.50, 36.70, 36.50}), min);
        check("36.5,36.7,36.5".equals(values(r3)), "dropFlaps respeta el cambio real: " + values(r3));

        // ---------- 3. mergeChanges ----------
        Method merge = MainActivity.class.getDeclaredMethod("mergeChanges",
                JSONArray.class, JSONArray.class, long.class);
        merge.setAccessible(true);
        Method lost = MainActivity.class.getDeclaredMethod("lostSeries", JSONArray.class, JSONArray.class);
        lost.setAccessible(true);
        Method hasNew = MainActivity.class.getDeclaredMethod("hasNewLocal", JSONArray.class, JSONArray.class);
        hasNew.setAccessible(true);
        long now = System.currentTimeMillis();
        long p1 = now - 86400000L, p2 = now - 43200000L;

        // nube con datos + local vacío -> la fusión conserva la nube
        JSONArray cloud = pts(new long[]{p1, p2}, new double[]{36.5, 36.7});
        JSONArray m1 = (JSONArray) merge.invoke(act, cloud, new JSONArray(), min);
        check(m1 != null && m1.length() == 2, "fusión con nube y local vacío: " + (m1 == null ? "null" : m1.length()));

        // nube vacía + local con datos -> no hay cambios (null) pero sí hay algo nuevo que subir
        JSONArray loc = pts(new long[]{p1, p2}, new double[]{36.5, 36.7});
        JSONArray m2 = (JSONArray) merge.invoke(act, null, loc, min);
        check(m2 == null, "fusión sin nube: null (se sube la serie local tal cual)");
        check(Boolean.TRUE.equals(hasNew.invoke(act, null, loc)), "hay cambios locales para subir");

        // RELOJ 6 h ATRÁS: los puntos de la nube se descartan por validTs -> la fusión sale
        // VACÍA. Con la guarda lostSeries la sincronización se aborta (si no, se subía la serie
        // vacía y se BORRABA el historial de todos).
        JSONArray future = pts(new long[]{now + 10L * 3600000L, now + 11L * 3600000L},
                new double[]{36.5, 36.7});
        JSONArray m3 = (JSONArray) merge.invoke(act, future, future, min);
        check(m3 != null && m3.length() == 0, "fusión vacía con reloj desincronizado: " + (m3 == null ? "null" : m3.length()));
        check(Boolean.TRUE.equals(lost.invoke(act, m3, future)), "lostSeries detecta la pérdida -> aborta");

        // nube corrupta (elemento no numérico) -> debe PROPAGAR el error (no devolver null:
        // con null el llamador subía solo lo local y borraba la nube de todos)
        JSONArray bad = new JSONArray();
        JSONObject bo = new JSONObject();
        bo.put("t", "abc");
        bo.put("v", 36.5);
        bad.put(bo);
        boolean threw = false;
        try {
            merge.invoke(act, bad, pts(new long[]{p1}, new double[]{36.5}), min);
        } catch (Exception e) {
            threw = true;
        }
        check(threw, "nube corrupta -> error (no null) para abortar la subida");

        // ---------- 4. Bancos S7B ----------
        check("0102".equals(S7BQr.codeFor("0102")), "codeFor(0102)");
        check("0102".equals(S7BQr.codeFor("Venezuela")), "codeFor(Venezuela) por alias");
        check("0102".equals(S7BQr.codeFor("banco Venezuela")), "codeFor(banco Venezuela)");
        check("0166".equals(S7BQr.codeFor("Banco Agrícola de Venezuela")), "codeFor(Banco Agrícola de Venezuela)");
        check(S7BQr.codeFor("b") == null, "codeFor(b) -> null (no elige un banco al azar)");
        check("V12345678".equals(S7BQr.normalizeId("12345678")), "normalizeId numérico");
        check(S7BQr.normalizeId("123ABC") == null, "normalizeId con letras -> null");
        check(S7BQr.normalizeId("V12345678") != null, "normalizeId V válido");
        check(S7BQr.normalizeId("1234567890123") == null, "normalizeId demasiado largo -> null");
        check(S7BQr.normalizeId("J12345678") != null, "normalizeId J válido");

        // ---------- 5. parseAmount (el monto del conversor) ----------
        Method parse = MainActivity.class.getDeclaredMethod("parseAmount", String.class);
        parse.setAccessible(true);
        check(parse.invoke(act, "899,30").equals(Double.valueOf(899.30)), "parseAmount(899,30) = " + parse.invoke(act, "899,30"));
        check(parse.invoke(act, "1.054,60").equals(Double.valueOf(1054.60)), "parseAmount(1.054,60) = " + parse.invoke(act, "1.054,60"));
        check(parse.invoke(act, "1.054").equals(Double.valueOf(1054.0)), "parseAmount(1.054) = miles → " + parse.invoke(act, "1.054"));
        check(parse.invoke(act, "12.500").equals(Double.valueOf(12500.0)), "parseAmount(12.500) = miles → " + parse.invoke(act, "12.500"));
        check(parse.invoke(act, "1054.60").equals(Double.valueOf(1054.60)), "parseAmount(1054.60) = decimal → " + parse.invoke(act, "1054.60"));
        check(parse.invoke(act, "0,5").equals(Double.valueOf(0.5)), "parseAmount(0,5) = " + parse.invoke(act, "0,5"));
        check(parse.invoke(act, "").equals(Double.valueOf(0.0)), "parseAmount(vacío) = 0");
        check(parse.invoke(act, "abc").equals(Double.valueOf(0.0)), "parseAmount(abc) = 0");

        System.out.println(fails == 0 ? "ALL TESTS PASSED" : (fails + " TEST(S) FAILED"));
        if (fails > 0) System.exit(1);
    }
}
